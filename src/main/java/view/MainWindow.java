package view;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.ReaderBasedJsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.ClientProtocolException;
import org.brunocvcunha.instagram4j.Instagram4j;
import org.brunocvcunha.instagram4j.requests.InstagramBlockRequest;
import org.brunocvcunha.instagram4j.requests.InstagramGetUserFollowersRequest;
import org.brunocvcunha.instagram4j.requests.InstagramGetUserInfoRequest;
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
    private JTextField passwordField;
    private JSpinner maxPostsSpinner;
    private JSpinner minFollowingSpinner;
    private JTextField foundField;
    private JTable followersTable;
    private JButton searchButton;
    private JPanel rootPanel;
    private JLabel feedback;

    private Instagram4j instagramApi;
    private String loggedUser;
    private String loggedPassword;

    private Random random;

    public MainWindow(){
        random = new Random();
        add(rootPanel);
        setTitle("Instagram Follower Cleaner - By Victorma");
        setSize(400, 600);
        minFollowingSpinner.setValue(1000);

        searchButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                searchButton.setEnabled(false);

                new Thread() {
                    public void run(){

                        int maxPosts = (Integer) maxPostsSpinner.getValue();
                        int minFollowing = (Integer) minFollowingSpinner.getValue();
                        int completed = 0;

                        feedback.setText("Logging in...");
                        Instagram4j igAPI = getAPI();

                        InstagramUser myUser = getUserInformation(igAPI.getUsername());
                        progressBar1.setMinimum(0);
                        progressBar1.setMaximum(myUser.follower_count);
                        progressBar1.setValue(0);

                        if (igAPI != null) {
                            try {
                                Vector columns = new Vector();
                                columns.add("Username");
                                columns.add("User ID");
                                columns.add("Posts");
                                columns.add("Followers");
                                columns.add("Following");
                                DefaultTableModel model = new DefaultTableModel(columns, 0);

                                boolean exit = false;

                                InstagramGetUserFollowersRequest request = new InstagramGetUserFollowersRequest(igAPI.getUserId());


                                do{
                                    InstagramGetUserFollowersResult followers;

                                    int retries = 0;
                                    boolean blocked = false;
                                    do{
                                        followers = instagramApi.sendRequest(request);
                                        if(followers.getError_type() != null) {
                                            retries++;
                                            feedback.setText("Error while retrieving the followers (" + followers.getError_type() +
                                                    "). Retrying in 30 seconds (" + retries + ")");
                                            Thread.sleep(30000);
                                        } else {
                                            blocked = true;
                                        }
                                    } while (!blocked);

                                    if(followers.getUsers().size() <= 1 || followers.next_max_id == null) {
                                        exit = true;
                                    }

                                    List<InstagramUserSummary> users = followers.getUsers();

                                    for (InstagramUserSummary user : users) {
                                        System.out.println("User " + user.getUsername() + " follows you!");
                                        InstagramUser completeUser =  null;
                                        while(completeUser == null){
                                            completeUser = getUserInformation(user.getUsername());
                                        }

                                        if(completeUser.media_count <= maxPosts && completeUser.following_count >= minFollowing) {
                                            model.addRow(new Object[]{user.getUsername(), user.getPk(), completeUser.media_count, completeUser.follower_count, completeUser.following_count});
                                        }
                                        completed++;
                                        float percent = Math.round((completed / (float) progressBar1.getMaximum())*1000) / 10f;
                                        feedback.setText("(" + (percent) +
                                                " %) Checked " + completed + " out of " + progressBar1.getMaximum() +
                                                " followers. (" + user.username + ")");
                                        progressBar1.setValue(completed);
                                    }

                                    request = new InstagramGetUserFollowersRequest(igAPI.getUserId(), followers.next_max_id);
                                    Thread.sleep(random.nextInt(1000) + 1000);
                                }while(!exit);

                                foundField.setText(model.getRowCount() + "");

                                followersTable.setModel(model);

                                searchButton.setEnabled(true);
                                cleanFollowersButton.setEnabled(true);
                                feedback.setText("Done!");
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            } catch (InterruptedException e1) {
                                e1.printStackTrace();
                            }
                        }
                    }
                }.start();

            }
        });


        cleanFollowersButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                searchButton.setEnabled(false);
                cleanFollowersButton.setEnabled(false);

                new Thread() {
                    public void run(){

                        feedback.setText("Logging in...");
                        Instagram4j igAPI = getAPI();

                        progressBar1.setMinimum(0);
                        progressBar1.setMaximum(followersTable.getRowCount());
                        progressBar1.setValue(0);

                        if (igAPI != null) {
                            try {

                                TableModel tableModel = followersTable.getModel();

                                for (int i = 0; i < tableModel.getRowCount(); i++) {

                                    String username = (String) tableModel.getValueAt(i, 0);
                                    Long id = (Long) tableModel.getValueAt(i, 1);

                                    int retries = 0;
                                    boolean blocked = false;
                                    do{
                                        StatusResult statusResult = instagramApi.sendRequest(new InstagramBlockRequest(id));
                                        if(statusResult.getError_type() != null) {
                                            retries++;
                                            feedback.setText("Error while blocking " + username + " (" + statusResult.getError_type() +
                                                    "). Retrying in 30 seconds (" + retries + ")");
                                            Thread.sleep(30000);
                                        } else {
                                            blocked = true;
                                        }
                                    } while (!blocked);

                                    System.out.println("User " + username + " Blocked!");

                                    float percent = Math.round(((i+1) / (float) progressBar1.getMaximum())*1000) / 10f;
                                    feedback.setText("(" + (percent) +
                                            " %) Blocked " + (i+1) + " out of " + progressBar1.getMaximum() +
                                            " followers. (" + username + ")");
                                    progressBar1.setValue(i+1);
                                    Thread.sleep(random.nextInt(10000) + 20000);
                                }

                                searchButton.setEnabled(true);
                                feedback.setText("Done!");
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            } catch (InterruptedException e1) {
                                e1.printStackTrace();
                            }
                        }
                    }
                }.start();

            }
        });
    }

    private Instagram4j getAPI() {

        if(checkLogged()){
            return instagramApi;
        }

        // Login to instagram
        loggedUser = usernameField.getText();
        loggedPassword = passwordField.getText();

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

        return  instagramApi;
    }

    private boolean checkLogged() {
        return instagramApi != null && usernameField.getText() == loggedUser && passwordField.getText() == loggedPassword;
    }

    int tooMany = 0;

    private InstagramUser getUserInformation(String username){

        InstagramUser instagramUser = new InstagramUser();
        instagramUser.username = username;

        Document doc = null;
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
