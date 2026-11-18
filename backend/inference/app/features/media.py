"""Prepare reviewed portrait crops without inferring demographic attributes."""

import base64
import io

import cv2
import numpy as np
from PIL import Image, ImageOps


def image_bytes(encoded: str, maximum: int = 4 * 1024 * 1024) -> bytes:
    """Decode validated base64 and reject empty or oversized image bytes."""
    if len(encoded) > maximum * 4 // 3 + 8:
        raise ValueError("Image exceeds its size limit.")
    value = base64.b64decode(encoded, validate=True)
    if not value or len(value) > maximum:
        raise ValueError("Image exceeds its size limit.")
    return value


def reviewed_crop(encoded: str) -> np.ndarray:
    """Decode bounded PNG/JPEG data and return RGB 224 x 224 pixels."""
    with Image.open(io.BytesIO(image_bytes(encoded))) as source:
        if source.format not in {"JPEG", "PNG"} or source.width * source.height > 8_000_000:
            raise ValueError("Use a bounded PNG or JPEG portrait.")
        return np.asarray(ImageOps.fit(ImageOps.exif_transpose(source).convert("RGB"), (224, 224)))


def portrait_candidates(pages: list[str]) -> list[str]:
    """Return up to four face candidates from the first two rendered PDF pages.

    Detection only suggests crops. The candidate must select or omit one;
    no picture is automatically treated as an accepted research input.
    """
    detector = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")
    found = []
    for encoded in pages[:2]:
        with Image.open(io.BytesIO(image_bytes(encoded))) as source:
            if source.width * source.height > 8_000_000:
                raise ValueError("Rendered page exceeds its pixel limit.")
            image = ImageOps.exif_transpose(source).convert("RGB")
            image.thumbnail((1600, 1600))
            pixels = np.asarray(image)
        gray = cv2.cvtColor(pixels, cv2.COLOR_RGB2GRAY)
        boxes = detector.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(40, 40))
        for x, y, width, height in boxes:
            margin = int(max(width, height) * 0.15)
            crop = Image.fromarray(pixels).crop((max(0, x-margin), max(0, y-margin),
                min(pixels.shape[1], x+width+margin), min(pixels.shape[0], y+height+margin)))
            output = io.BytesIO()
            ImageOps.fit(crop, (224, 224)).save(output, format="JPEG", quality=90)
            found.append((width * height, base64.b64encode(output.getvalue()).decode("ascii")))
    return [value for _, value in sorted(found, reverse=True)[:4]]
