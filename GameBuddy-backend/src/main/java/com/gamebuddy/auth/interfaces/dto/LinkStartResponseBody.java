package com.gamebuddy.auth.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Where to send the user to prove they own the account.
 *
 * <p>The app opens this in the system browser rather than a WebView. A provider may refuse to render
 * its login inside a frame, both providers are entering a password, and a password typed
 * into a browser the app controls is a password the app could have read — the address bar
 * is the only thing that tells somebody they are really on discord.com.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LinkStartResponseBody implements BaseModel {
    private String authorizeUrl;
}
