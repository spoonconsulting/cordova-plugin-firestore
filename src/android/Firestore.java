package com.spoon.cordova.firestore;

import androidx.annotation.Nullable;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Firestore extends CordovaPlugin {
    private static final String TAG = "FIRESTORE_PLUGIN";
    private FirebaseFirestore db;
    private Auth auth;
    private final Map<String, CallbackContext> realtimeCallbacks = new HashMap<>();
    private final Map<String, ListenerRegistration> listenerRegistrations = new HashMap<>();

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        try { 
            switch (action) {
                case "getDocumentData":
                    getDocumentData(args.getString(0), args.getString(1), args.getJSONArray(2), callbackContext);
                    break;
                case "setDocumentData":
                    setDocumentData(args.getString(0), args.getString(1), args.getJSONArray(2), args.getJSONObject(3), callbackContext);
                    break;
                case "listenToDocument":
                    listenToDocument(args.getString(0), args.getString(1), args.getJSONArray(2), callbackContext);
                    break;
                case "signInWithCustomToken":
                    initAuth();
                    this.auth.signInWithCustomToken(args.getString(0), callbackContext);
                    break;
                default:
                    callbackContext.error("execute <-> Invalid action: " + action);
                    return false;
            }
        } catch (Exception e) {
            handleExceptionWithContext("execute", e, callbackContext);
            return false;
        }

        return true;
    }

    private void getDocumentData(String collection, String docId, JSONArray pathSegments, CallbackContext context) {
        DocumentReference docRef;
        try {
            docRef = getDocumentRef(collection, docId, pathSegments);
        } catch (Exception e) {
            handleExceptionWithContext("getDocumentData", e, context);
            return;
        }

        init();
        docRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    JSONObject data = new JSONObject();
                    try {
                        data.put("data", mapToJSONObject(document.getData()));
                    } catch (Exception e) {
                        handleExceptionWithContext("getDocumentData", e, context);
                        return;
                    }
                    PluginResult result = new PluginResult(PluginResult.Status.OK, data);
                    context.sendPluginResult(result);
                } else {
                    Exception e = new Exception("No such document");
                    handleExceptionWithContext("getDocumentData", e, context);
                }
            } else {
                Exception e = task.getException();
                if (e == null) {
                    e = new Exception("Error getting document data");
                }
                handleExceptionWithContext("getDocumentData", e, context);
            }
        });
    }

    private void setDocumentData(String collection, String docId, JSONArray pathSegments, JSONObject data, CallbackContext context) {
        DocumentReference docRef;
        Map<String, Object> dataMap;
        try {
            docRef = getDocumentRef(collection, docId, pathSegments);
        } catch (Exception e) {
            handleExceptionWithContext("setDocumentData", e, context);
            return;
        }

        try {
            dataMap = jsonObjectToMap(data);
        } catch (Exception e) {
            handleExceptionWithContext("setDocumentData", e, context);
            return;
        }

        init();
        docRef.set(dataMap, SetOptions.merge())
                .addOnSuccessListener(aVoid -> context.success())
                .addOnFailureListener(e -> handleExceptionWithContext("setDocumentData", e, context));
    }

    private void listenToDocument(String collection, String docId, JSONArray pathSegments, CallbackContext context) {
        DocumentReference docRef;
        String docPath;
        try {
            docRef = getDocumentRef(collection, docId, pathSegments);
        } catch (Exception e) {
            handleExceptionWithContext("listenToDocument", e, context);
            return;
        }

        try {
            docPath = getDocumentPath(collection, docId, pathSegments);
        } catch (Exception e) {
            handleExceptionWithContext("listenToDocument", e, context);
            return;
        }

        if (realtimeCallbacks.containsKey(docPath)) return;

        init();
        realtimeCallbacks.put(docPath, context);
        ListenerRegistration registration = docRef.addSnapshotListener((snapshot, e) -> {
            JSONObject resultData = new JSONObject();
            if (e != null) {
                try {
                    resultData.put("error", e.getMessage());
                } catch (JSONException ignored) {}
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, resultData);
                Objects.requireNonNull(realtimeCallbacks.get(docPath)).sendPluginResult(result);
                realtimeCallbacks.remove(docPath);
                Objects.requireNonNull(listenerRegistrations.get(docPath)).remove();
                listenerRegistrations.remove(docPath);
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                try {
                    resultData.put("data", mapToJSONObject(snapshot.getData()));
                } catch (Exception ignored) {}
                PluginResult result = new PluginResult(PluginResult.Status.OK, resultData);
                result.setKeepCallback(true);
                Objects.requireNonNull(realtimeCallbacks.get(docPath)).sendPluginResult(result);
            }
        });
        listenerRegistrations.put(docPath, registration);
    }

    private DocumentReference getDocumentRef(String collection, String docId, JSONArray pathSegments) throws IllegalArgumentException, JSONException {
        if (pathSegments.length() % 2 != 0) {
            throw new IllegalArgumentException("Path segments must be in pairs");
        }

        init();
        DocumentReference docRef = this.db.collection(collection).document(docId);
        if (pathSegments.length() == 0) return docRef;

        for (int i = 0; i < pathSegments.length(); i += 2) {
            docRef = docRef.collection(pathSegments.getString(i)).document(pathSegments.getString(i + 1));
        }
        return docRef;
    }

    private void init() {
        if (this.db != null) return;

        this.db = FirebaseFirestore.getInstance();
    }

    private void initAuth() {
        if (this.auth != null) return;

        this.auth = new Auth();
    }

    /*
     * Helper methods
     */
    private void handleExceptionWithContext(String caller, Exception e, CallbackContext context) {
        String msg = e.toString();
        e.printStackTrace();
        context.error(TAG + ": " + caller + " <-> " + msg);
    }

    private Map<String, Object> jsonObjectToMap(JSONObject jsonObject) throws Exception {
        TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonString = jsonObject.toString();
        return objectMapper.readValue(jsonString, typeRef);
    }

    private JSONObject mapToJSONObject(Map<String, Object> map) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonString = objectMapper.writeValueAsString(map);
        return new JSONObject(jsonString);
    }

    private String getDocumentPath(String collection, String docId, JSONArray pathSegments) throws Exception {
        StringBuilder path = new StringBuilder(collection + "/" + docId);
        if (pathSegments.length() == 0) return path.toString();

        for (int i = 0; i < pathSegments.length(); i++) {
            path.append("/").append(pathSegments.getString(i));
        }
        return path.toString();
    }

    private void cleanupListenerRegistrations() {
        for (ListenerRegistration reg : listenerRegistrations.values()) {
            reg.remove();
        }
        listenerRegistrations.clear();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanupListenerRegistrations();
    }
}