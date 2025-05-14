package com.spoon.cordova.firestore;

import androidx.annotation.Nullable;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
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

public class Auth {
    private FirebaseAuth auth;
    private static final String TAG = "FIRESTORE_PLUGIN";

//    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
//        try {
//            switch (action) {
//                case "signInWithCustomToken":
//                    signInWithCustomToken(args.getString(0));
//                    break;
//                default:
//                    callbackContext.error("execute <-> Invalid action: " + action);
//                    return false;
//            }
//        } catch (Exception e) {
//            handleExceptionWithContext("execute", e, callbackContext);
//            return false;
//        }
//
//        return true;
//    }

    public void signInWithCustomToken(String token, CallbackContext context) {
        init();
        if (isSignedIn()) {
            context.success();
        }

        this.auth.signInWithCustomToken(token)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            context.success();
                        } else {
                            Exception e = task.getException();
                            if (e == null) {
                                e = new Exception("Error signing in with custom token");
                            }
                            handleExceptionWithContext("getDocumentData", e, context);
                        }
                    });
    }

    private void init() {
        if (this.auth != null) return;

        this.auth = FirebaseAuth.getInstance();
    }

    private boolean isSignedIn() {
        init();
        return this.auth.getCurrentUser() != null;
    }

    /*
     * Helper methods
     */
    private void handleExceptionWithContext(String caller, Exception e, CallbackContext context) {
        String msg = e.toString();
        e.printStackTrace();
        context.error(TAG + ": " + caller + " <-> " + msg);
    }
}