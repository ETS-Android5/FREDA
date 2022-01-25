package ca.freda.relation_annotator.handler;

import android.os.Environment;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import ca.freda.relation_annotator.MainActivity;

public class CommunicationHandler {

    private ClientHandler clientHandler;
    private ServerHandler serverHandler;
    private HandlerThread clientThread;

    private MainActivity mainActivity;
    private String uid;
    private JSONObject reData;
    private JSONObject crData;
    private JSONObject nerData;
    private JSONObject elData;
    private JSONObject elAbstracts;
    private JSONObject elAliases;

    private JSONObject relation;
    private JSONObject crDataset;
    private JSONObject nerDataset;
    private JSONObject elDataset;

    private int currentRow = 0;

    private boolean online = false;

    public CommunicationHandler(MainActivity mainActivity, String uid) {
        this.mainActivity = mainActivity;
        this.uid = uid;
        this.reData = readFile("relations.json","assets");
        this.crData = readFile("coref.json","assets");
        this.nerData = readFile("ner.json","assets");
        this.elData = readFile("coref.json","assets");


        String downloads_directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        File[] path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).listFiles();

        this.elAbstracts = readFile(downloads_directory + "/abstracts_100000.json","storage");
        this.elAliases = readFile(downloads_directory + "/aliases_100000.json","storage");
    }

    private JSONObject readFile(String filename, String type) {
        JSONObject result = null;
        try {
            BufferedReader br;
            if (type.equals("assets")) {
                br = new BufferedReader(new InputStreamReader(mainActivity.getAssets().open(filename)));
            } else {
                br = new BufferedReader(new FileReader(filename));
            }

            String content = "";
            String line = br.readLine();
            while (line != null){
                content += line;
                line = br.readLine();
            }
            result = new JSONObject(content);

        } catch (IOException | JSONException ex) {
            ex.printStackTrace();
        }

        return result;
    }

    public void startClient() {
        if (online) {
            System.out.println("start client");
            clientThread = new HandlerThread("Socket Thread");
            clientThread.start();
            Looper mLooper = clientThread.getLooper();
            serverHandler = new ServerHandler(mainActivity.getMainLooper(), mainActivity);
            clientHandler = new ClientHandler(mLooper, mainActivity);
        }
    }

    public void restartClient() {
        if (online) {
            System.out.println("reset client");
            clientThread.interrupt();
            startClient();
        }
    }

    public void passMessageToServerHandler(Message msg) {
        if (online) {
            this.serverHandler.sendMessage(msg);
        }
    }

    public void sendMessage(JSONObject message) {
        try {
            if (online) {
                startClient();
                try {
                    message.put("uid", uid);
                    if (relation != null) {
                        message.put("relation", relation.getString("name"));
                    }
                    clientHandler.sendMessage(message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("send offline message");
                int mode = message.getInt("mode");
                String task = message.getString("task");
                switch (mode) {
                    case 0:
                        break;
                    case 1:
                        if (task.equals("RE")) {
                            // REAnnotationFragment asks for data.
                            message = this.reData.getJSONArray(relation.getString("name")).getJSONObject(currentRow);
                            message.put("task","RE");
                            mainActivity.receiveMessage(message);
                        } else if (task.equals("CR")) {
                            // CRAnnotationFragment asks for data.
                            message = this.crData.getJSONArray(crDataset.getString("name")).getJSONObject(currentRow);
                            message.put("task","CR");
                            mainActivity.receiveMessage(message);
                        } else if (task.equals("NER")) {
                            // NERAnnotationFragment asks for data.
                            message = this.nerData.getJSONArray(nerDataset.getString("name")).getJSONObject(currentRow);
                            message.put("task","NER");
                            mainActivity.receiveMessage(message);
                        } else if (task.equals("EL")) {
                            // ELAnnotationFragment asks for data.
                            message = this.nerData.getJSONArray(elDataset.getString("name")).getJSONObject(currentRow);
                            message.put("task","EL");
                            mainActivity.receiveMessage(message);
                        }
                        currentRow++;
                        if (currentRow >= 1000) {
                            currentRow = 0;
                        }
                        break;
                    case 2:
                        break;
                    case 3:
                        break;
                    case 4:
                        currentRow -= 2;
                        if (currentRow < 0) {
                            currentRow = 999;
                        }
                        break;
                    case 5:


                        if (task.equals("RE")) {
                            // get all relations
                            this.reData.put("mode", 5);
                            this.reData.put("task","RE");
                            mainActivity.receiveMessage(this.reData);
                        } else if (task.equals("CR")) {
                            // get coref datasets
                            this.crData.put("mode", 5);
                            this.crData.put("task","CR");
                            mainActivity.receiveMessage(this.crData);
                        } else if (task.equals("NER")) {
                            // get coref datasets
                            this.nerData.put("mode", 5);
                            this.nerData.put("task","NER");
                            mainActivity.receiveMessage(this.nerData);
                        } else if (task.equals("EL")) {
                            // get coref datasets
                            this.elData.put("mode", 5);
                            this.elData.put("task","EL");
                            mainActivity.receiveMessage(this.elData);
                        }

                        break;
                    case 6:
                        if (task.equals("EL")) {
                            // annotator asks for candidates
                            String mention = message.getString("mention");
                            JSONObject candidates = new JSONObject();
                            candidates.put("NO ENTITY","");
                            if (elAliases.has(mention)) {
                                JSONArray entities = elAliases.getJSONArray(mention);
                                for (int i = 0; i < entities.length(); i++) {
                                    String candidate = entities.getString(i);
                                    if (elAbstracts.has(candidate)) {
                                        candidates.put(candidate,elAbstracts.getString(candidate));
                                    }
                                }
                            }
                            message.put("candidates",candidates);
                            mainActivity.receiveMessage(message);
                        }

                        break;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public Message obtainMessage() {
        if (online) {
            return clientHandler.obtainMessage();
        } else {
            return null;
        }
    }

    public void sendMessage(Message message) {
        if (online) {
            clientHandler.sendMessage(message);
        }
    }

    public JSONObject getRelation() {
        return relation;
    }

    public void setRelation(JSONObject relation) {
        this.currentRow = 0;
        this.relation = relation;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public JSONObject getCrDataset() {
        return crDataset;
    }

    public void setCrDataset(JSONObject crDataset) {
        this.crDataset = crDataset;
    }

    public JSONObject getNerDataset() {
        return nerDataset;
    }

    public void setNerDataset(JSONObject nerDataset) {
        this.nerDataset = nerDataset;
    }

    public JSONObject getElDataset() {
        return elDataset;
    }

    public void setElDataset(JSONObject elDataset) {
        this.elDataset = elDataset;
    }

    public JSONObject getElAbstracts() {
        return elAbstracts;
    }

    public JSONObject getElAliases() {
        return elAliases;
    }
}