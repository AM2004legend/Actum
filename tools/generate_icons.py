"""Builds every Actum icon the Android app ships from one picture, tools/actum-icon.png.

That picture is the mark as designed: a black "A" on a beige rounded square, on
black. This lifts the letter off its ground and writes:

  mipmap-*/ic_launcher_foreground.png   the letter, for the adaptive icon
  mipmap-*/ic_launcher_monochrome.png   the same, for themed icons (the system tints it)
  mipmap-*/ic_launcher_background.png   the beige
  mipmap-*/ic_launcher.png, _round.png  the whole icon, for launchers without adaptive icons
  drawable-*/splash_icon.png            the letter, for the launch screen
  drawable-*/ic_tile.png                the letter in white, for the Quick Settings tile
  drawable-nodpi/mark.png               the letter in white, tinted by the app wherever it shows its mark

Run from the repo root:  python tools/generate_icons.py
"""
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "tools" / "actum-icon.png"
RES = ROOT / "android" / "app" / "src" / "main" / "res"

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def letter_and_ground():
    """The letter as an alpha mask cropped to its own edges, and the beige behind it."""
    picture = Image.open(SOURCE).convert("RGB")
    ground = picture.getpixel((picture.width // 2, picture.height // 8))
    grey = picture.convert("L")
    # Dark is the letter, but so are the black corners outside the rounded square.
    # Flood those from each corner with white, and only the letter stays dark.
    for corner in [(0, 0), (grey.width - 1, 0), (0, grey.height - 1), (grey.width - 1, grey.height - 1)]:
        ImageDraw.floodfill(grey, corner, 255, thresh=120)
    light = sum(ground) / 3
    mask = grey.point(lambda v: max(0, min(255, round((light - v) * 255 / light))))
    # The rounded square's own soft edge is left as a faint ring where the flood stopped.
    # It is far from the letter: clear everything outside the letter's solid extent.
    solid = mask.point(lambda a: 255 if a > 128 else 0).getbbox()
    box = (solid[0] - 6, solid[1] - 6, solid[2] + 6, solid[3] + 6)
    return mask.crop(box), ground


def placed(mask, canvas, share, colour):
    """[mask] filled with [colour], centred on a clear square [canvas] wide, its longer side [share] of it."""
    scale = canvas * share / max(mask.size)
    size = (max(1, round(mask.width * scale)), max(1, round(mask.height * scale)))
    small = mask.resize(size, Image.LANCZOS)
    layer = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    fill = Image.new("RGBA", size, colour + (255,))
    layer.paste(fill, ((canvas - size[0]) // 2, (canvas - size[1]) // 2), small)
    return layer


def shaped(image, radius_share):
    """[image] clipped to a rounded square; 0.5 makes it a circle."""
    big = image.width * 4
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, big - 1, big - 1), radius=big * radius_share, fill=255)
    out = image.copy()
    out.putalpha(ImageChops.multiply(out.getchannel("A"), mask.resize(image.size, Image.LANCZOS)))
    return out


def save(image, folder, name):
    target = RES / folder
    target.mkdir(parents=True, exist_ok=True)
    image.save(target / name, optimize=True)


def main():
    mask, ground = letter_and_ground()
    black = (10, 10, 10)
    for density, factor in DENSITIES.items():
        # Adaptive layers are 108 dp; launchers show the middle 72 and may crop to 66, so the letter stays inside that.
        layer = round(108 * factor)
        letter = placed(mask, layer, 0.5, black)
        save(letter, f"mipmap-{density}", "ic_launcher_foreground.png")
        save(letter, f"mipmap-{density}", "ic_launcher_monochrome.png")
        save(Image.new("RGBA", (layer, layer), ground + (255,)), f"mipmap-{density}", "ic_launcher_background.png")

        legacy = round(48 * factor)
        whole = Image.new("RGBA", (legacy, legacy), ground + (255,))
        whole.alpha_composite(placed(mask, legacy, 0.66, black))
        save(shaped(whole, 0.22), f"mipmap-{density}", "ic_launcher.png")
        save(shaped(whole, 0.5), f"mipmap-{density}", "ic_launcher_round.png")

        # The launch screen shows a 288 dp picture through a circle two thirds as wide.
        save(placed(mask, round(288 * factor), 0.42, black), f"drawable-{density}", "splash_icon.png")
        save(placed(mask, round(24 * factor), 0.92, (255, 255, 255)), f"drawable-{density}", "ic_tile.png")
    # The mark the app draws on its own screens, in white for the app to tint.
    save(placed(mask, 256, 1.0, (255, 255, 255)), "drawable-nodpi", "mark.png")
    print("ground", "#%02X%02X%02X" % ground)


if __name__ == "__main__":
    main()
