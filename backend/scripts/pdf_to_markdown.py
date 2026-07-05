"""Read a decrypted PDF from binary stdin and write its Markdown to stdout.

Used by the backend's MarkdownService: the decrypted statement bytes are piped
in via stdin and the Markdown is read from stdout, so the PDF never touches disk
(CardLens security rule). Requires: pip install "markitdown[pdf]"
"""
import io
import sys

from markitdown import MarkItDown, StreamInfo

data = sys.stdin.buffer.read()
result = MarkItDown().convert_stream(
    io.BytesIO(data),
    stream_info=StreamInfo(extension=".pdf", mimetype="application/pdf"),
)
sys.stdout.buffer.write(result.text_content.encode("utf-8"))
