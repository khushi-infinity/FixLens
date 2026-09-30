"""Image intake: validation, size limits, and model-ready preprocessing.

Uploaded images are never stored permanently — bytes live only in memory for
the duration of the request (spec §14: do not store uploads by default).
"""
import io
import statistics
from typing import Tuple

from PIL import Image, ImageOps, ImageStat

MAX_UPLOAD_BYTES = 10 * 1024 * 1024  # 10 MB hard limit at the API boundary
MODEL_MAX_DIMENSION = 1280           # px, longest edge sent to the model
MODEL_JPEG_QUALITY = 80

# Quality-gate thresholds (spec §14: reject obviously unusable images rather
# than asking the model to guess).
MIN_EFFECTIVE_DIMENSION = 160        # px on the shorter side after resize
MIN_MEAN_LUMA = 18                   # 0-255: below this the frame is near-black
MAX_MEAN_LUMA = 248                  # near-white/blown-out frame
MIN_LUMA_STDDEV = 6.0                # below this the frame has (almost) no detail

# Magic-byte signatures for accepted formats.
_JPEG = b"\xff\xd8\xff"
_PNG = b"\x89PNG\r\n\x1a\n"
_WEBP = b"RIFF"


class ImageValidationError(Exception):
    """Controlled image problem mapped to a 4xx response."""

    def __init__(self, user_message: str):
        super().__init__(user_message)
        self.user_message = user_message


def validate_upload(data: bytes) -> None:
    if len(data) == 0:
        raise ImageValidationError("The uploaded image is empty.")
    if len(data) > MAX_UPLOAD_BYTES:
        raise ImageValidationError(
            "This image is too large (limit 10 MB). Retake the photo or use a smaller image."
        )
    if not (
        data.startswith(_JPEG) or data.startswith(_PNG) or data[:4] == _WEBP
    ):
        raise ImageValidationError(
            "Unsupported image format. Use a JPEG or PNG photo from the camera."
        )


def _check_quality(img: Image.Image) -> None:
    """Raises ImageValidationError for frames a vision model cannot use.
    Deliberately conservative: only rejects clearly unusable images —
    borderline shots are left to the model + needs_better_view."""
    short_side = min(img.size)
    if short_side < MIN_EFFECTIVE_DIMENSION:
        raise ImageValidationError(
            "This image is too small to analyze. Move closer to the object and capture it again."
        )
    luma = img.convert("L")
    stat = ImageStat.Stat(luma)
    mean = stat.mean[0]
    stddev = statistics.fmean(stat.stddev) if isinstance(stat.stddev, (list, tuple)) else stat.stddev
    if mean < MIN_MEAN_LUMA:
        raise ImageValidationError(
            "This image is too dark to analyze. Add light and capture it again."
        )
    if mean > MAX_MEAN_LUMA:
        raise ImageValidationError(
            "This image is too bright to analyze. Reduce glare or move away from direct light, then capture again."
        )
    if stddev < MIN_LUMA_STDDEV:
        raise ImageValidationError(
            "This image has no visible detail. Check that the lens is unobstructed, focus on the object, and capture again."
        )


def prepare_for_model(data: bytes) -> Tuple[bytes, int, int]:
    """Returns (jpeg_bytes, width, height) downscaled for the vision model.

    Free-tier cost control (spec §15): a 12 MP camera photo becomes a
    ~1280 px JPEG, typically 10x smaller in upload bytes and tokens.
    Unusable frames (near-black, blown out, lens-blocked, too small) are
    rejected here — before any provider call or quota spend.
    """
    try:
        with Image.open(io.BytesIO(data)) as img:
            img.load()
            img = ImageOps.exif_transpose(img)
            if img.mode != "RGB":
                img = img.convert("RGB")
            _check_quality(img)
            img.thumbnail((MODEL_MAX_DIMENSION, MODEL_MAX_DIMENSION), Image.LANCZOS)
            out = io.BytesIO()
            img.save(out, format="JPEG", quality=MODEL_JPEG_QUALITY, optimize=True)
            width, height = img.size
        return out.getvalue(), width, height
    except ImageValidationError:
        raise
    except Exception as exc:
        raise ImageValidationError(
            "We couldn't read this image. Try capturing it again."
        ) from exc
