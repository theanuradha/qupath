package qupath.lib.gui.commands;

import com.dropbox.core.*;
import com.dropbox.core.json.JsonReader;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;

import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class DropboxConnectCommand implements PathCommand {

    private class DropboxJson {
        private String key;
        private String secret;
        private String auth;
    }

    private final QuPathGUI qupath;
    private final String username;
    private final String password;

    public DropboxConnectCommand(final QuPathGUI qupath, final String username, final String password) {
        this.qupath = qupath;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run() {
        String property = "java.io.tmpdir";

        // Get the temporary directory
        String tempDir = System.getProperty(property);
        // TODO remove old files in this temp folder
        // TODO set project dir to this temp folder


        // Read app info file (contains app key and app secret)
        URL dropboxJsonUrl = this.getClass().getResource("/dropbox.json");
        DbxAppInfo appInfo;
        try {
            appInfo = DbxAppInfo.Reader.readFromFile(dropboxJsonUrl.getPath());
        } catch (JsonReader.FileLoadException ex) {
            System.err.println("Error reading <app-info-file>: " + ex.getMessage());
            return;
        }

        // Run through Dropbox API authorization process
        // TODO change client identifier
        DbxRequestConfig requestConfig = new DbxRequestConfig("qupath_projects_business");
        // TODO we need to use the "Token" connect instead of the "code" connect
        // https://github.com/dropbox/dropbox-sdk-java/blob/master/examples/authorize/src/main/java/com/dropbox/core/examples/authorize/Main.java

        DbxWebAuth webAuth = new DbxWebAuth(requestConfig, appInfo);
        DbxWebAuth.Request webAuthRequest = DbxWebAuth.newRequestBuilder()
                .withNoRedirect()
                .build();

        String authorizeUrl = webAuth.authorize(webAuthRequest);

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(authorizeUrl));
            }
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }

        DbxAuthFinish authFinish;
        try {
            String token = DisplayHelpers.showInputDialog("Dropbox Authorization", "Enter token", "");
            authFinish = webAuth.finishFromCode(token);
        } catch (DbxException ex) {
            System.err.println("Error in DbxWebAuth.authorize: " + ex.getMessage());
            return;
        }
        int e = 0;
    }
}
