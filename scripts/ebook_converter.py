#!/usr/bin/env python3
"""
Ebook converter script for MOBI/AZW3 to EPUB conversion
Usage: python ebook_converter.py <input_file> <output_file>
"""

import sys
import os
import tempfile
import shutil
from pathlib import Path


def convert_mobi_to_epub(input_path, output_path):
    """Convert MOBI file to EPUB format"""
    try:
        import mobi
        from ebooklib import epub
        from bs4 import BeautifulSoup
        import base64

        # Extract MOBI content
        result = mobi.read(input_path)

        if result is None:
            return False, "Failed to read MOBI file"

        # mobi.read returns (html_content, metadata, images_dict)
        if isinstance(result, tuple):
            html_content = result[0] if len(result) > 0 else None
            metadata = result[1] if len(result) > 1 else {}
            images = result[2] if len(result) > 2 else {}
        else:
            html_content = result
            metadata = {}
            images = {}

        if not html_content:
            return False, "No content extracted from MOBI file"

        # Create EPUB book
        book = epub.EpubBook()

        # Set metadata
        title = metadata.get("Title", "Untitled")
        author = metadata.get("Author", "Unknown")
        book.set_title(title)
        book.set_author(author)

        # Parse HTML content
        soup = BeautifulSoup(html_content, "lxml")

        # Extract title from HTML if available
        title_tag = soup.find("title")
        if title_tag:
            book.set_title(title_tag.get_text().strip() or title)

        # Create chapter from main content
        chapter = epub.EpubHtml(title="Content", file_name="chap_01.xhtml", lang="en")

        # Process images and embed them
        img_map = {}
        for img_id, img_data in images.items():
            if img_data:
                ext = "jpg"
                img_filename = f"images/{img_id}.{ext}"
                epub_image = epub.EpubImage()
                epub_image.file_name = img_filename
                epub_image.media_type = f"image/{ext}"
                epub_image.content = img_data
                book.add_item(epub_image)
                img_map[img_id] = img_filename

        # Update image references in HTML
        for img in soup.find_all("img"):
            src = img.get("src", "")
            if src in img_map:
                img["src"] = img_map[src]
            elif src.startswith("kindle:embed:"):
                img_id = src.split(":")[-1]
                if img_id in img_map:
                    img["src"] = img_map[img_id]

        chapter.content = str(soup.body) if soup.body else str(soup)

        book.add_item(chapter)

        # Add NCX and Nav
        book.add_item(epub.EpubNcx())
        book.add_item(epub.EpubNav())

        # Set spine
        book.spine = ["nav", chapter]

        # Set TOC
        book.toc = (chapter,)

        # Write EPUB file
        epub.write_epub(output_path, book, {})

        return True, "Conversion successful"

    except Exception as e:
        return False, str(e)


def convert_azw3_to_epub(input_path, output_path):
    """
    Convert AZW3 file to EPUB format.
    Note: AZW3 (KF8) is complex and may require Calibre for full support.
    This function attempts basic conversion using mobi library.
    """
    try:
        import mobi

        # Try using mobi library (may have limited AZW3 support)
        result = mobi.read(input_path)

        if result is None:
            return (
                False,
                "AZW3 format requires Calibre for conversion. Please install Calibre (brew install --cask calibre)",
            )

        return convert_mobi_to_epub(input_path, output_path)

    except Exception as e:
        return (
            False,
            f"AZW3 conversion failed: {str(e)}. Install Calibre for better support.",
        )


def main():
    if len(sys.argv) != 3:
        print(
            "Usage: python ebook_converter.py <input_file> <output_file>",
            file=sys.stderr,
        )
        sys.exit(1)

    input_path = sys.argv[1]
    output_path = sys.argv[2]

    if not os.path.exists(input_path):
        print(f"Error: Input file not found: {input_path}", file=sys.stderr)
        sys.exit(1)

    # Determine input format
    ext = Path(input_path).suffix.lower()

    if ext == ".mobi":
        success, message = convert_mobi_to_epub(input_path, output_path)
    elif ext == ".azw3":
        success, message = convert_azw3_to_epub(input_path, output_path)
    else:
        print(f"Error: Unsupported format: {ext}", file=sys.stderr)
        sys.exit(1)

    if success:
        print(f"Success: {message}")
        sys.exit(0)
    else:
        print(f"Error: {message}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
