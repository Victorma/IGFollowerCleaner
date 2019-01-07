package view;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.ClientProtocolException;
import org.brunocvcunha.instagram4j.Instagram4j;
import org.brunocvcunha.instagram4j.requests.InstagramBlockRequest;
import org.brunocvcunha.instagram4j.requests.InstagramGetUserFollowersRequest;
import org.brunocvcunha.instagram4j.requests.payload.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.Vector;

public class MainWindow extends JFrame {
    private JProgressBar progressBar1;
    private JButton cleanFollowersButton;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JSpinner maxPostsSpinner;
    private JSpinner minFollowingSpinner;
    private JTextField foundField;
    private JTable followersTable;
    private JButton searchButton;
    private JPanel rootPanel;
    private JLabel feedback;
    private JButton searchAndCleanButton;
    private JCheckBox noProfilePicCheckBox;
    private JCheckBox emptyBioCheckBox;

    private Instagram4j instagramApi;
    private String loggedUser;
    private String loggedPassword;

    private Random random;

    private class SearchAndCleanActionListener implements ActionListener {

        private boolean cleanToo;

        private SearchAndCleanActionListener(boolean cleanToo) {
            this.cleanToo = cleanToo;
        }

        public void actionPerformed(ActionEvent e) {

            disableForm();
            cleanFollowersButton.setEnabled(false);

            new Thread() {
                public void run(){

                    int completed = 0;

                    feedback.setText("Logging in...");
                    Instagram4j igAPI = getAPI();
                    if (igAPI == null){
                        return;
                    }

                    InstagramUser myUser;
                    do {
                        myUser = getUserInformation(igAPI.getUsername());
                    }while(myUser == null);
                    progressBar1.setMinimum(0);
                    progressBar1.setMaximum(myUser.follower_count);
                    progressBar1.setValue(0);
                    Vector<String> columns = new Vector<String>();
                    columns.add("Username");
                    columns.add("User ID");
                    columns.add("Posts");
                    columns.add("Following");
                    columns.add("Pic?");
                    columns.add("Bio?");
                    columns.add("Verified");
                    DefaultTableModel model = new DefaultTableModel(columns, 0);
                    followersTable.setModel(model);

                    boolean exit = false;

                    InstagramGetUserFollowersRequest request = new InstagramGetUserFollowersRequest(igAPI.getUserId());

                    do{
                        InstagramGetUserFollowersResult followersResult = getFollowers(request);
                        List<InstagramUserSummary> users = followersResult.getUsers();

                        for (InstagramUserSummary user : users) {
                            System.out.println("User " + user.getUsername() + " follows you! " + user.getPk());
                            InstagramUser completeUser =  null;

                            while(completeUser == null){
                                // Get the full user information
                                completeUser = getUserInformation(user.getUsername());
                            }

                            int posts = completeUser.media_count;
                            int following = completeUser.following_count;
                            boolean notHasPic = user.has_anonymous_profile_picture;
                            boolean notHasBio = StringUtils.isEmpty(completeUser.biography);
                            boolean isVerified = completeUser.is_verified;

                            if (isBot(posts, following, notHasPic, notHasBio, isVerified)) {
                                model.addRow(new Object[]{user.getUsername(), user.getPk(), posts, following,
                                        !notHasPic, !notHasBio, isVerified});
                                foundField.setText(model.getRowCount() + "");

                                if (cleanToo) {
                                    blockUser(user.getUsername(), user.getPk());
                                }
                            }

                            completed++;
                            float percent = Math.round((completed / (float) progressBar1.getMaximum())*1000) / 10f;
                            feedback.setText("(" + (percent) +
                                    " %) Checked " + completed + " out of " + progressBar1.getMaximum() +
                                    " followers. (" + user.username + ")");
                            progressBar1.setValue(completed);
                        }

                        if(users.size() <= 1 || followersResult.next_max_id == null) {
                            exit = true;
                        }

                        request = new InstagramGetUserFollowersRequest(igAPI.getUserId(), followersResult.next_max_id);
                    }while(!exit);

                    enableForm();
                    cleanFollowersButton.setEnabled(true);

                    feedback.setText("Done!");
                }
            }.start();
        }
    }

    public MainWindow(){
        random = new Random();
        add(rootPanel);
        setTitle("Instagram Follower Cleaner - By Victorma");
        setSize(400, 600);
        minFollowingSpinner.setValue(1000);

        searchButton.addActionListener(new SearchAndCleanActionListener(false));
        searchAndCleanButton.addActionListener(new SearchAndCleanActionListener(true));

        cleanFollowersButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                disableForm();

                new Thread() {
                    public void run(){

                        feedback.setText("Logging in...");
                        Instagram4j igAPI = getAPI();
                        if (igAPI == null){
                            return;
                        }

                        progressBar1.setMinimum(0);
                        progressBar1.setMaximum(followersTable.getRowCount());
                        progressBar1.setValue(0);
                        TableModel tableModel = followersTable.getModel();

                        for (int i = 0; i < tableModel.getRowCount(); i++) {

                            String username = (String) tableModel.getValueAt(i, 0);
                            Long id = (Long) tableModel.getValueAt(i, 1);

                            blockUser(username, id);

                            float percent = Math.round(((i+1) / (float) progressBar1.getMaximum())*1000) / 10f;
                            feedback.setText("(" + (percent) +
                                    " %) Blocked " + (i+1) + " out of " + progressBar1.getMaximum() +
                                    " followers. (" + username + ")");
                            progressBar1.setValue(i+1);
                        }

                        cleanFollowersButton.setEnabled(false);
                        enableForm();
                        feedback.setText("Done!");
                    }
                }.start();

            }
        });
    }

    private boolean isBot(int posts, int following, boolean notHasPic, boolean notHasBio, boolean isVerified) {

        int maxPosts = (Integer) maxPostsSpinner.getValue();
        int minFollowing = (Integer) minFollowingSpinner.getValue();

        boolean isBot = !isVerified && posts <= maxPosts && following >= minFollowing;

        if(emptyBioCheckBox.isSelected()){
            isBot &= notHasBio;
        }

        if(noProfilePicCheckBox.isSelected()) {
            isBot &= notHasPic;
        }
        return isBot;
    }

    private void enableForm() {
        usernameField.setEnabled(true);
        passwordField.setEnabled(true);
        searchButton.setEnabled(true);
        searchAndCleanButton.setEnabled(true);
        maxPostsSpinner.setEnabled(true);
        minFollowingSpinner.setEnabled(true);
        noProfilePicCheckBox.setEnabled(true);
        emptyBioCheckBox.setEnabled(true);
    }

    private void disableForm() {
        usernameField.setEnabled(false);
        passwordField.setEnabled(false);
        searchButton.setEnabled(false);
        searchAndCleanButton.setEnabled(false);
        maxPostsSpinner.setEnabled(false);
        minFollowingSpinner.setEnabled(false);
        noProfilePicCheckBox.setEnabled(false);
        emptyBioCheckBox.setEnabled(false);
    }

    private InstagramGetUserFollowersResult getFollowers(InstagramGetUserFollowersRequest request) {
        int retries = 0;
        InstagramGetUserFollowersResult followersResult = null;
        boolean obtained = false;
        do{
            String error = "";
            try{
                // Sleep a little bit before obtaining the page
                try {
                    Thread.sleep(random.nextInt(1000) + 500);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }

                // Perform the block
                followersResult = instagramApi.sendRequest(request);
                if(followersResult.getError_type() != null) {
                    // In case of expected errors
                    error = followersResult.getError_type();
                } else {
                    obtained = true;
                }
            } catch (IOException e1) {
                // In case of unsexpected errors
                e1.printStackTrace();
                error = e1.getMessage();
            }

            if(!obtained) {
                // If not blocked we retry
                feedback.setText("Error while retrieving the followers (" + error +
                        "). Retrying in 30 seconds (" + retries + ")");
                retries++;
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        } while (!obtained);

        System.out.println("Next page of followers obtained!");

        return followersResult;
    }

    private void blockUser(String username, Long id) {
        int retries = 0;
        boolean blocked = false;
        do{
            String error = "";
            try{
                float percent = Math.round((progressBar1.getValue() / (float) progressBar1.getMaximum())*1000) / 10f;
                feedback.setText("(" + (percent) + " %) Blocking " + username);
                // Sleep a little bit before blocking the user
                try {
                    Thread.sleep(random.nextInt(4000) + 3000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }

                // Perform the block
                StatusResult statusResult = instagramApi.sendRequest(new InstagramBlockRequest(id));
                if(statusResult.getError_type() != null) {
                    // In case of expected errors
                    error = statusResult.getError_type();
                } else {
                    blocked = true;
                }
            } catch (IOException e1) {
                // In case of unsexpected errors
                e1.printStackTrace();
                error = e1.getMessage();
            }

            if(!blocked) {
                // If not blocked we retry
                feedback.setText("Error while blocking " + username + " (" + error +
                        "). Retrying in 30 seconds (" + retries + ")");
                retries++;
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        } while (!blocked);

        System.out.println("User " + username + " Blocked!");
    }

    private Instagram4j getAPI() {

        if(checkLogged()){
            return instagramApi;
        }

        // Login
        loggedUser = usernameField.getText();
        loggedPassword = new String(passwordField.getPassword());

        if(!StringUtils.isEmpty(loggedUser.trim()) && !StringUtils.isEmpty(loggedPassword.trim())){
            instagramApi = Instagram4j.builder().username(loggedUser).password(loggedPassword).build();
            instagramApi.setup();

            try {
                InstagramLoginResult loginResult = instagramApi.login();
                if(loginResult.getError_type() != null) {
                    JOptionPane.showMessageDialog(null, "Login failed (" + loginResult.getError_type() + ")");
                    instagramApi = null;
                }
            } catch (ClientProtocolException e) {
                JOptionPane.showMessageDialog(null, "Login exception!");
                instagramApi = null;
                e.printStackTrace();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Login exception!");
                instagramApi = null;
                e.printStackTrace();
            }
        } else {
            JOptionPane.showMessageDialog(null, "Insert a user and a pass!");
            instagramApi = null;
        }


        if(instagramApi == null) {
            enableForm();
            feedback.setText("Login failed!");
        }

        return  instagramApi;
    }

    private boolean checkLogged() {
        return instagramApi != null && usernameField.getText().equals(loggedUser) && new String(passwordField.getPassword()).equals(loggedPassword);
    }

    private int tooMany = 0;

    private InstagramUser getUserInformation(String username){

        InstagramUser instagramUser = new InstagramUser();
        instagramUser.username = username;

        Document doc;
        try {
            doc = Jsoup.connect("https://www.instagram.com/" + username + "/").get();

            Elements scripts = doc.getElementsByTag("script");
            for(Element script : scripts) {
                if(script.data().startsWith("window._sharedData = ")) {
                    String sharedData = script.data().substring("window._sharedData = ".length());
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode rootNode = objectMapper.readTree(sharedData);
                    JsonNode userNode = rootNode.get("entry_data").get("ProfilePage").get(0).get("graphql").get("user");

                    instagramUser.media_count = userNode.get("edge_owner_to_timeline_media").get("count").intValue();
                    instagramUser.follower_count = userNode.get("edge_followed_by").get("count").intValue();
                    instagramUser.following_count = userNode.get("edge_follow").get("count").intValue();
                    instagramUser.is_verified = userNode.get("is_verified").booleanValue();
                    instagramUser.biography = userNode.get("biography").textValue();

                    tooMany = 0;
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            try {
                // Wait 30 seconds before trying again
                tooMany++;
                float percent = Math.round((progressBar1.getValue() / (float) progressBar1.getMaximum())*1000) / 10f;
                feedback.setText("(" + (percent) +
                        " %) Too many requests, waiting 30 seconds (" + tooMany + ")");
                Thread.sleep(30000);
                return null;
            } catch (InterruptedException ee) {
                e.printStackTrace();
            }
        }

        return instagramUser;
    }

}
