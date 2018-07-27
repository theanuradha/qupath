package qupath.lib.gui.commands;

import com.dropbox.core.*;
import com.dropbox.core.json.JsonReader;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.*;
import com.dropbox.core.v2.sharing.AddMember;
import com.dropbox.core.v2.sharing.ListFoldersResult;
import com.dropbox.core.v2.sharing.MemberSelector;
import com.dropbox.core.v2.users.FullAccount;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;

import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

public class DropboxConnectCommand implements PathCommand {

    private final QuPathGUI qupath;
    private final String dbxTokenFile = "dbx_token.json";
    private final String ROOT_FOLDER_ATTR = "root_folder";
    private final String SHARE_WITH_TEAM_ATTR = "share_with_team";
    private final String SHARED_WITH_ATTR = "shared_with";

    public DropboxConnectCommand(final QuPathGUI qupath) {
        this.qupath = qupath;
    }

    private DbxAuthFinish authUser(DbxAppInfo appInfo, DbxRequestConfig requestConfig)
            throws URISyntaxException, IOException, DbxException {

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

    private void saveAuth(DbxAppInfo appInfo, String accessToken, String outputFolder) throws IOException {
        // Save auth information to output file.
        DbxAuthInfo authInfo = new DbxAuthInfo(accessToken, appInfo.getHost());
        File output = new File(Paths.get(outputFolder, dbxTokenFile).toString());
        DbxAuthInfo.Writer.writeToFile(authInfo, output);
        System.out.println("Saved authorization information to \"" + output.getCanonicalPath() + "\".");
    }

    private boolean isDropboxTokenExist(String filePath) {
        File f = new File(filePath);
        return f.exists() && !f.isDirectory();
    }

    private Map<String, Object> getDropboxConfig(String configFilePath)
            throws IOException, ParseException {
        Map<String, Object> ret = new HashMap<>();

        JSONParser parser = new JSONParser();
        JSONObject obj = (JSONObject) parser.parse(new FileReader(configFilePath));
        ret.put(ROOT_FOLDER_ATTR, obj.get(ROOT_FOLDER_ATTR));
        ret.put(SHARE_WITH_TEAM_ATTR, Boolean.valueOf((String) obj.get(SHARE_WITH_TEAM_ATTR)));
        ret.put(SHARED_WITH_ATTR, obj.get(SHARED_WITH_ATTR));
        return ret;
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

        javafx.scene.control.Label helpText = new javafx.scene.control.Label("You can chose to sync " +
                "your project with Dropbox or to use it locally");
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
        String tempDir = Paths.get(System.getProperty(property), "qupath_dbx").toString();

        try {
            DbxRequestConfig requestConfig = new DbxRequestConfig("qupath_projects");
            URL dropboxJsonUrl = this.getClass().getResource("/dropbox.json");
            String configFilePath = dropboxJsonUrl.getPath();
            Map<String, Object> dbxConfig = getDropboxConfig(configFilePath);
            String dbxRootPath = (String) dbxConfig.get(ROOT_FOLDER_ATTR);
            boolean shareWithTeam = (Boolean) dbxConfig.get(SHARE_WITH_TEAM_ATTR);
            List<String> shareUserList = (List<String>) dbxConfig.get(SHARED_WITH_ATTR);

            String accessToken = null;

            // Read app info file (contains app key and app secret)
            DbxAppInfo appInfo = DbxAppInfo.Reader.readFromFile(configFilePath);

            if (!isDropboxTokenExist(Paths.get(tempDir, dbxTokenFile).toString())) {
                // Clean temporary directory
                FileUtils.forceMkdir(new File(tempDir));
                FileUtils.cleanDirectory(new File(tempDir));
                DbxAuthFinish authFinish = authUser(appInfo, requestConfig);
                accessToken = authFinish.getAccessToken();
                saveAuth(appInfo, accessToken, tempDir);
            } else {
                JSONParser parser = new JSONParser();
                JSONObject obj = (JSONObject) parser.parse(new FileReader(Paths.get(tempDir, dbxTokenFile).toString()));
                accessToken =  (String) obj.get("access_token");
            }

            DbxClientV2 client = new DbxClientV2(requestConfig, accessToken);
            FullAccount account = client.users().getCurrentAccount();

            // Get files and folder metadata from Dropbox root directory
            String qupathFolder = "/" + account.getName().getGivenName() + "_" +
                    account.getName().getSurname() + "_qupath_projects";
            try {
                client.files().createFolderV2(qupathFolder);
            } catch (CreateFolderErrorException ignored) { }

            String qupathFolderId = ((FolderMetadata) client.files().getMetadata(qupathFolder)).getId();
            List<AddMember> shareWithList = new ArrayList<>();
//            if (shareWithTeam && account.getTeam() != null) {
//                shareWithList.add(new AddMember(MemberSelector.dropboxId(account.getTeam().getId())));
//            }

            for (String user: shareUserList) {
                shareWithList.add(new AddMember(MemberSelector.email(user)));
            }
            // https://stackoverflow.com/questions/33592326/dropbox-api-v2-returns-invalid-shared-folder-id
            ListFoldersResult resultShare = client.sharing().listFolders();
            ListFolderResult result = client.files().listFolder("");
            client.sharing().addFolderMember(qupathFolderId, shareWithList);
            while (true) {
                for (Metadata metadata : result.getEntries()) {
                    System.out.println(metadata.getPathLower());
                }

                if (!result.getHasMore()) {
                    break;
                }

                result = client.files().listFolderContinue(result.getCursor());
            }
            int e = 0;

            // TODO Once the temp folder is synchronized use the project files in there
            //Project<BufferedImage> project = ProjectIO.loadProject(fileNew, BufferedImage.class);
            //qupath.setProject();

        } catch (DbxException | JsonReader.FileLoadException | URISyntaxException | IOException | ParseException e) {
            e.printStackTrace();
        }

    }
}
