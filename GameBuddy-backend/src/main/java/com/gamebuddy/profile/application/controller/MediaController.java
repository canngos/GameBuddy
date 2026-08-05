package com.gamebuddy.profile.application.controller;

import com.gamebuddy.shared.storage.ObjectNotFoundException;
import com.gamebuddy.shared.storage.ObjectStorage;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves approved media from local storage. Development only.
 *
 * <p>In production R2 serves these bytes directly and this application never sees an image
 * request. Locally there is no R2, so something has to answer the URL that
 * {@code LocalObjectStorage.publicUrl} hands out — otherwise every avatar is a broken
 * image the moment you run without credentials, and the feature can only be exercised by
 * someone who has them.
 *
 * <p><strong>The public bucket only.</strong> The bucket is fixed here, so an image
 * awaiting review cannot be fetched by guessing its key. That is the one mistake that
 * would undo the two-bucket split, and it is prevented by there being no parameter for it.
 *
 * <p>Conditional on the local implementation being active: with R2 configured this bean
 * does not exist, so a production deployment cannot end up serving images out of the
 * application process.
 */
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
@ConditionalOnBean(name = "localMediaMarker")
public class MediaController {

    private static final String PREFIX = "/media/";

    private final ObjectStorage storage;

    @GetMapping("/**")
    public ResponseEntity<byte[]> get(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith(PREFIX)) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] bytes = storage.get(ObjectStorage.Bucket.MEDIA, uri.substring(PREFIX.length()));
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    // Keys are unique per upload and never rewritten, so the bytes behind
                    // one can be cached forever. This mirrors what R2 does in production
                    // rather than being a local-only shortcut.
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                    .body(bytes);
        } catch (ObjectNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
