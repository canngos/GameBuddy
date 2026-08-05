package com.gamebuddy.shared.moderation;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Asks the Python service whether an image is sexual content.
 *
 * <p>The bytes are posted rather than a URL. That keeps object-storage credentials out of
 * the model service entirely, and it means nobody who can reach that service can point it
 * at an arbitrary host — a URL parameter there would be a server-side request forgery hole
 * into the private network both processes sit in.
 */
@HttpExchange
public interface ImageModerationClient {

    /** @return the verdict and the raw score behind it */
    @PostExchange(value = "/moderate/image", contentType = MediaType.MULTIPART_FORM_DATA_VALUE)
    ModerationResponse moderate(@RequestPart("file") Resource file);

    record ModerationResponse(String verdict, double score) {}
}
