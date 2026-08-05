package com.gamebuddy.shared.storage;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

/**
 * Turns whatever was uploaded into an image we produced ourselves.
 *
 * <p>The bytes a client sends are never the bytes that get stored. Decoding and re-encoding
 * costs a few milliseconds and buys three things that are awkward to get any other way:
 *
 * <ul>
 *   <li><strong>EXIF is gone.</strong> Phone photographs carry GPS coordinates, and an
 *       avatar that tells strangers where its owner lives is a safety problem on an app
 *       that matches people — considerably worse for the under-18 half of the population.
 *       {@code ImageIO} writes only pixels, so re-encoding drops every metadata block
 *       without needing a library that understands them. Client-side stripping is not a
 *       substitute: the client is whatever posted the request.
 *   <li><strong>Trailing data is gone.</strong> A file can be a valid JPEG followed by an
 *       archive, a script, or a second image. Decoders stop at the end of the image and
 *       ignore the rest; other software does not. Only the decoded pixels survive here.
 *   <li><strong>The format is ours.</strong> One content type, one extension, no
 *       animated-payload surprises, and a size bound that holds regardless of what was
 *       sent.
 * </ul>
 *
 * <p>Dimensions are read from the header before any pixels are allocated. A 40KB PNG can
 * declare a 50,000×50,000 canvas, which is ten gigabytes of heap on decode and the
 * cheapest denial of service there is against an upload endpoint.
 */
@Component
public class ImageNormaliser {

    /** Avatars are shown at 128px at the very largest; 512 leaves room for a bigger design. */
    public static final int MAX_DIMENSION = 512;

    /** Refused on the declared dimensions, before decode. Far above any real photograph. */
    public static final long MAX_PIXELS = 40_000_000L;

    public static final String CONTENT_TYPE = "image/jpeg";
    public static final String EXTENSION = "jpg";

    /** Not an image, or an image we will not decode. */
    public static class UnreadableImageException extends RuntimeException {
        public UnreadableImageException(String message) {
            super(message);
        }
    }

    /**
     * Decodes, squares, scales down and re-encodes as JPEG.
     *
     * @return the bytes to store — never the bytes passed in
     */
    public byte[] normalise(byte[] source) {
        BufferedImage decoded = decode(source);
        BufferedImage square = centreCrop(decoded);
        BufferedImage scaled = scaleDown(square);
        return encode(scaled);
    }

    private BufferedImage decode(byte[] source) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (stream == null) {
                throw new UnreadableImageException("Not a readable image");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new UnreadableImageException("Not a recognised image format");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                // Header only. The dimensions are checked before anything is allocated.
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new UnreadableImageException("Image is too large to process");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new UnreadableImageException("Image could not be decoded");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new UnreadableImageException("Not a readable image: " + e.getMessage());
        }
    }

    /** Centre crop to a square, so the client never has to reason about aspect ratio. */
    private BufferedImage centreCrop(BufferedImage image) {
        int side = Math.min(image.getWidth(), image.getHeight());
        int x = (image.getWidth() - side) / 2;
        int y = (image.getHeight() - side) / 2;
        return image.getSubimage(x, y, side, side);
    }

    private BufferedImage scaleDown(BufferedImage image) {
        int side = Math.min(image.getWidth(), MAX_DIMENSION);

        // TYPE_INT_RGB, not the source type: a PNG with transparency would otherwise
        // become a JPEG with black where the alpha was, and a palette image would carry
        // its palette into a format that has none.
        BufferedImage target = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, side, side, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] encode(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, EXTENSION, out)) {
                throw new UnreadableImageException("No JPEG writer available");
            }
        } catch (IOException e) {
            throw new UnreadableImageException("Could not re-encode the image: " + e.getMessage());
        }
        return out.toByteArray();
    }
}
