package com.fixlens.app.network

import com.fixlens.app.data.BackendConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException

/** Failure kinds the UI can react to without parsing provider-specific details. */
sealed class ApiError : Exception() {
    /** Config is missing/malformed, the developer must fix device config. */
    class NotConfigured(message: String) : ApiError()

    /** Backend unreachable, timeout, or DNS failure. */
    class Unreachable(override val message: String, override val cause: Throwable? = null) :
        ApiError()

    /**
     * The request took longer than the per-call window. Distinct from
     * [Unreachable]: the backend may still be generating, so the honest
     * message asks the user to retry rather than blaming the connection
     * (Phase 8: an 86 s free-tier diagnose once surfaced as "could not
     * reach" because 90 s was too tight, the wording must not lie).
     */
    class Timeout(override val message: String) : ApiError()

    /** Backend answered with a non-2xx status. */
    class Http(val code: Int, override val message: String) : ApiError()

    /** Backend answered 2xx but the body did not match the expected contract. */
    class InvalidResponse(override val message: String) : ApiError()
}

/**
 * Phase 1 API surface. Exactly one endpoint is implemented: GET /health.
 * The abstraction (config injection + typed errors) is what later phases build
 * diagnosis/plan/target/verify calls on top of, no provider logic lives here.
 */
class ApiClient private constructor(
    private val config: BackendConfig,
    private val client: OkHttpClient,
    private val json: Json,
) {

    suspend fun health(): BackendHealth {
        val request = Request.Builder()
            .url("${config.normalizedUrl}/health")
            .get()
            .build()
        return execute(request) { body -> json.decodeFromString<BackendHealth>(body) }
    }

    /**
     * Phase 1 placeholder for the future /v1/diagnose call: proves the multipart
     * path works against the local backend without inventing an AI response.
     * The backend answers 501 until a later phase implements diagnosis.
     */
    suspend fun probeDiagnose(image: File): DiagnoseProbeResult {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                image.name,
                image.asRequestBody("image/jpeg".toMediaType()),
            )
            .build()
        val request = Request.Builder()
            .url("${config.normalizedUrl}/v1/diagnose")
            .post(requestBody)
            .build()
        return try {
            execute(request) { body ->
                json.decodeFromString<BackendHealth>(body)
                DiagnoseProbeResult.UnexpectedSuccess
            }
        } catch (error: ApiError.Http) {
            if (error.code == 501) DiagnoseProbeResult.NotImplemented else throw error
        }
    }

    sealed class DiagnoseProbeResult {
        object NotImplemented : DiagnoseProbeResult()
        object UnexpectedSuccess : DiagnoseProbeResult()
    }

    /**
     * Phase 2: sends one JPEG to POST /api/v1/diagnose and returns the
     * normalized diagnosis + deterministic safety decision. The backend owns
     * provider selection, validation, and safety gating, the app only renders
     * the validated result and maps transport failures to honest UI states.
     *
     * Phase 7 reliability: the AI timeout is per-call (image calls are slow,
     * plan/verify/diagnose get a generous window) so a stalled backend can
     * never wedge the UI on the shared 20s config timeout.
     */
    suspend fun diagnose(
        image: File,
        mode: String = "PHOTO",
        context: String? = null,
        timeoutMillis: Long = AI_CALL_TIMEOUT_MILLIS,
    ): DiagnoseResponseDto {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                image.name,
                image.asRequestBody("image/jpeg".toMediaType()),
            )
            .addFormDataPart("mode", mode)
            .apply { context?.let { addFormDataPart("context", it) } }
            .build()
        val request = Request.Builder()
            .url("${config.normalizedUrl}/api/v1/diagnose")
            .post(requestBody)
            .build()
        return execute(request, timeoutMillis) { body ->
            json.decodeFromString<DiagnoseResponseDto>(body)
        }
    }

    /**
     * Phase 4: asks the backend to turn a validated diagnosis into a
     * structured repair plan. The full diagnosis JSON is sent as the body,
     * the backend re-validates it and runs the deterministic safety gate
     * BEFORE any generation (HIGH risk never reaches the model).
     */
    suspend fun plan(
        diagnosis: DiagnosisDto,
        context: String? = null,
        timeoutMillis: Long = AI_CALL_TIMEOUT_MILLIS,
    ): PlanResponseDto {
        val payload = buildPlanRequestBody(diagnosis, context)
        val request = Request.Builder()
            .url("${config.normalizedUrl}/api/v1/plan")
            .post(
                payload.toRequestBody("application/json; charset=utf-8".toMediaType()),
            )
            .build()
        return execute(request, timeoutMillis) { body ->
            json.decodeFromString<PlanResponseDto>(body)
        }
    }

    /**
     * Phase 4 assembly mode: sends a photo of disassembled parts and returns
     * the structured assembly plan (or a view request when the order cannot
     * be determined from this shot).
     */
    suspend fun planAssembly(
        image: File,
        context: String? = null,
        timeoutMillis: Long = AI_CALL_TIMEOUT_MILLIS,
    ): AssemblyResponseDto {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                image.name,
                image.asRequestBody("image/jpeg".toMediaType()),
            )
            .apply { context?.let { addFormDataPart("context", it) } }
            .build()
        val request = Request.Builder()
            .url("${config.normalizedUrl}/api/v1/assembly")
            .post(requestBody)
            .build()
        return execute(request, timeoutMillis) { body ->
            json.decodeFromString<AssemblyResponseDto>(body)
        }
    }

    /** Serializes the diagnosis exactly as the backend's PlanRequest expects. */
    private fun buildPlanRequestBody(diagnosis: DiagnosisDto, context: String?): String {
        val diagnosisJson = json.encodeToJsonElement(DiagnosisDto.serializer(), diagnosis)
        val root = buildJsonObject {
            put("diagnosis", diagnosisJson)
            if (!context.isNullOrBlank()) put("context", JsonPrimitive(context))
        }
        return root.toString()
    }

    /**
     * Phase 5: verifies one completed step against a fresh capture. One frame
     * per explicit user action, the client never streams frames (spec §15).
     * The backend re-validates the image with the same quality gate as
     * diagnosis and judges expected-vs-observed only; verification is kept
     * strictly separate from diagnosis (spec §17.7).
     */
    suspend fun verify(
        image: File,
        stepNumber: Int,
        expectedState: String,
        stepAction: String? = null,
        targetComponent: String? = null,
        userConfirmsDone: Boolean = true,
        timeoutMillis: Long = AI_CALL_TIMEOUT_MILLIS,
    ): VerifyResponseDto {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                image.name,
                image.asRequestBody("image/jpeg".toMediaType()),
            )
            .addFormDataPart("step_number", stepNumber.toString())
            .addFormDataPart("expected_state", expectedState)
            .apply { stepAction?.let { addFormDataPart("step_action", it) } }
            .apply { targetComponent?.let { addFormDataPart("target_component", it) } }
            .addFormDataPart("user_confirms_done", userConfirmsDone.toString())
            .build()
        val request = Request.Builder()
            .url("${config.normalizedUrl}/api/v1/verify")
            .post(requestBody)
            .build()
        return execute(request, timeoutMillis) { body ->
            json.decodeFromString<VerifyResponseDto>(body)
        }
    }

    private suspend fun <T> execute(
        request: Request,
        timeoutMillis: Long = config.timeoutMillis,
        parse: (String) -> T,
    ): T {
        // Network I/O must never run on the Main dispatcher, the UI calls
        // suspend functions from lifecycleScope (Main). Moving off Main here
        // protects every current and future endpoint, not just this call site.
        return withContext(Dispatchers.IO) {
            // AI endpoints are slow by nature; give them their own window
            // without touching the shared client's connect timeout.
            val callClient = if (timeoutMillis != config.timeoutMillis) {
                client.newBuilder()
                    .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                    .writeTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                    .build()
            } else {
                client
            }
            executeBlocking(callClient, request, parse)
        }
    }

    private fun <T> executeBlocking(
        client: OkHttpClient,
        request: Request,
        parse: (String) -> T,
    ): T {
        val response: Response = try {
            client.newCall(request).execute()
        } catch (e: java.io.IOException) {
            // OkHttp surfaces a read/write timeout as SocketTimeoutException
            // (an IOException subclass), it means the backend was reached but
            // did not answer in time, which deserves its own honest wording.
            if (e is java.net.SocketTimeoutException) {
                throw ApiError.Timeout(
                    "This is taking longer than expected, the AI is busy right now. " +
                        "Please try again in a moment.",
                )
            }
            throw ApiError.Unreachable(
                "Could not reach the FixLens backend at ${config.normalizedUrl}",
                e,
            )
        }
        response.use { resp ->
            val bodyString = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                // The backend sends {"detail": "user-friendly message"} on
                // controlled errors, surface that text instead of a bare code.
                val detail = runCatching {
                    json.parseToJsonElement(bodyString).jsonObject["detail"]?.jsonPrimitive?.content
                }.getOrNull()
                val message = detail?.takeIf { it.isNotBlank() }
                    ?: "Backend returned HTTP ${resp.code}"
                throw ApiError.Http(resp.code, message)
            }
            return try {
                parse(bodyString)
            } catch (e: Exception) {
                throw ApiError.InvalidResponse(
                    "Backend response did not match the expected format: ${e.message}",
                )
            }
        }
    }

    companion object {
        /**
         * Phase 8: AI endpoints (diagnose/plan/assembly/verify) run a vision
         * model with a bounded fallback chain. Free-tier latency observed in
         * the wild reaches ~90s for a single call (86s diagnose during the
         * Phase 8 E2E), so the window must exceed the slowest legitimate
         * response, not merely the average one. /health keeps the shared
         * config timeout.
         */
        const val AI_CALL_TIMEOUT_MILLIS = 150_000L

        fun create(
            config: BackendConfig,
            client: OkHttpClient = defaultOkHttp(config),
            json: Json = defaultJson(),
        ): ApiClient = ApiClient(config, client, json)

        private fun defaultOkHttp(config: BackendConfig): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(config.timeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(config.timeoutMillis, TimeUnit.MILLISECONDS)
                .writeTimeout(config.timeoutMillis, TimeUnit.MILLISECONDS)
                .build()

        private fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
