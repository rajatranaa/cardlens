"""Read a decrypted PDF from binary stdin and write its Markdown to stdout.

Primary path: markitdown (markitdown[pdf]) — extracts the PDF text layer and keeps
tables as Markdown. Fallback: if that text layer is garbled — e.g. "(cid:NN)" from
an embedded font with no Unicode mapping — render the pages and OCR them with
Tesseract instead. Binary-safe (uses sys.stdin.buffer), so the PDF never touches disk.

Requires: pip install "markitdown[pdf]" pymupdf pytesseract pillow  +  Tesseract OCR.
"""
import io
import os
import re
import shutil
import sys

from markitdown import MarkItDown, StreamInfo


def markitdown_text(data: bytes) -> str:
    return MarkItDown().convert_stream(
        io.BytesIO(data),
        stream_info=StreamInfo(extension=".pdf", mimetype="application/pdf"),
    ).text_content


def looks_garbled(text: str) -> bool:
    """True when the extracted text is unusable (empty or missing-font glyphs)."""
    if not text or not text.strip():
        return True
    # (cid:NN) tokens mean the font has no ToUnicode map -> characters are lost.
    return len(re.findall(r"\(cid:\d+\)", text)) > 20


def _find_tesseract() -> str | None:
    for c in (
        os.environ.get("TESSERACT_CMD"),
        shutil.which("tesseract"),
        os.path.expandvars(r"%LOCALAPPDATA%\Programs\Tesseract-OCR\tesseract.exe"),
        r"C:\Program Files\Tesseract-OCR\tesseract.exe",
    ):
        if c and os.path.exists(c):
            return c
    return None


def ocr_text(data: bytes) -> str:
    """Render each page at 300 dpi and OCR it with Tesseract."""
    import fitz  # PyMuPDF
    import pytesseract
    from PIL import Image

    exe = _find_tesseract()
    if exe:
        pytesseract.pytesseract.tesseract_cmd = exe

    parts = []
    with fitz.open(stream=data, filetype="pdf") as doc:
        for page in doc:
            pix = page.get_pixmap(dpi=300)
            img = Image.open(io.BytesIO(pix.tobytes("png")))
            parts.append(pytesseract.image_to_string(img))
    return "\n\n".join(parts)


def main() -> None:
    data = sys.stdin.buffer.read()
    text = markitdown_text(data)
    if looks_garbled(text):
        try:
            ocr = ocr_text(data)
            if ocr and not looks_garbled(ocr):
                text = ocr
        except Exception as e:  # OCR is best-effort; keep the original on failure
            sys.stderr.write(f"OCR fallback failed: {e}\n")
    sys.stdout.buffer.write(text.encode("utf-8"))


if __name__ == "__main__":
    main()
