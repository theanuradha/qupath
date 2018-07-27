package qupath.lib.gui.commands;

import com.dropbox.core.*;
import com.dropbox.core.json.JsonReader;
import com.dropbox.core.v2.DbxAppClientV2;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.sharing.DbxUserSharingRequests;
import com.dropbox.core.v2.sharing.ListFoldersResult;
import com.dropbox.core.v2.users.FullAccount;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;

import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

public class DropboxConnectCommand implements PathCommand {

    private final QuPathGUI qupath;

    public DropboxConnectCommand(final QuPathGUI qupath) {
        this.qupath = qupath;
    }

    private DbxAuthFinish authUser(String configFilePath, DbxRequestConfig requestConfig)
            throws JsonReader.FileLoadException, URISyntaxException, IOException, DbxException {
        // Read app info file (contains app key and app secret)
        DbxAppInfo appInfo;
        appInfo = DbxAppInfo.Reader.readFromFile(configFilePath);

        // Run through Dropbox API authorization process
        DbxWebAuth webAuth = new DbxWebAuth(requestConfig, appInfo);
        DbxWebAuth.Request webAuthRequest = DbxWebAuth.newRequestBuilder()
                .withNoRedirect()
                .build();

        String authorizeUrl = webAuth.authorize(webAuthRequest);

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI(authorizeUrl));
        }

        String token = DisplayHelpers.showInputDialog("Dropbox Authorization", "Enter token", "");
        return webAuth.finishFromCode(token);
    }

    private Image loadDropboxIcon(int size) {
        String path = "icons/dropbox_" + size + ".png";
        Image img = qupath.getImage(path);
        if (img != null) return img;
        return null;
    }

    private boolean showDialog() {
        // Show a setup message
        javafx.scene.control.Dialog<ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Connect to Dropbox");
        dialog.initOwner(qupath.getStage());

        // Try to get an image to display
        Image img = loadDropboxIcon(128);
        BorderPane pane = new BorderPane();
        if (img != null) {
            StackPane imagePane = new StackPane(new ImageView(img));
            imagePane.setPadding(new javafx.geometry.Insets(10, 10, 10, 10));
            pane.setLeft(imagePane);
        }

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25, 25, 25, 25));

        javafx.scene.control.Label helpText = new javafx.scene.control.Label("You can chose to sync your project with Dropbox or to use it locally");
        grid.add(helpText, 0, 0);

        ButtonType connectBtn = new ButtonType("Connect to Dropbox", ButtonBar.ButtonData.OK_DONE);
        pane.setCenter(grid);
        dialog.getDialogPane().setContent(pane);
        dialog.getDialogPane().getButtonTypes().setAll(new ButtonType("Use local files"), connectBtn);
        Optional<ButtonType> result = dialog.showAndWait();

        return result.isPresent() && connectBtn.equals(result.get());
    }

    @Override
    public void run() {
        boolean connect = showDialog();

        if (!connect) return;

        String property = "java.io.tmpdir";

        // Get the temporary directory
        String tempDir = System.getProperty(property);
        // TODO remove old files in this temp folder
        // TODO set project dir to this temp folder

        try {
            URL dropboxJsonUrl = this.getClass().getResource("/dropbox.json");
            String configFilePath = dropboxJsonUrl.getPath();
            DbxRequestConfig requestConfig = new DbxRequestConfig("qupath_projects");
            DbxAuthFinish authFinish = authUser(configFilePath, requestConfig);

            DbxClientV2 client = new DbxClientV2(requestConfig, authFinish.getAccessToken());

            FullAccount account = client.users().getCurrentAccount();
            System.out.println(account.getName().getDisplayName());

            // Get files and folder metadata from Dropbox root directory
            ListFoldersResult result = client.sharing().listFolders();
//            while (true) {
//                for (Metadata metadata : result.getEntries()) {
//                    System.out.println(metadata.getPathLower());
//                }
//
//                if (!result.getHasMore()) {
//                    break;
//                }
//
//                result = client.files().listFolderContinue(result.getCursor());
//            }
            int e = 0;
        } catch (DbxException | JsonReader.FileLoadException | URISyntaxException | IOException e) {
            e.printStackTrace();
        }

    }
}
