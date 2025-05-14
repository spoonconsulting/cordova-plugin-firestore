var PLUGIN_NAME = "Firestore";

var cordova = require('cordova');
var exec = require('cordova/exec');

var Firestore = function () {}
Firestore.realtimeCallbacks = {};

Firestore.signInWithCustomToken = function (options, onSuccess, onError) {
  exec(onSuccess, onError, PLUGIN_NAME, "signInWithCustomToken", [options.token]);
};

Firestore.setDocumentData = function (options, onSuccess, onError) {
  exec(onSuccess, onError, PLUGIN_NAME, "setDocumentData", [options.collection, options.docId, options.pathSegments, options.data]);
};

Firestore.listenToDocument = function (options, onSuccess, onError, callback) {
  if (Firestore.realtimeCallbacks[`${options.collection}/${options.docId}/${options.pathSegments.join("/")}`]) return;

  Firestore.realtimeCallbacks[`${options.collection}/${options.docId}/${options.pathSegments.join("/")}`] = callback;
  exec((data) => {
    Firestore.realtimeCallbacks[`${options.collection}/${options.docId}/${options.pathSegments.join("/")}`](data)
  }, onError, PLUGIN_NAME, "listenToDocument", [options.collection, options.docId, options.pathSegments]);
  onSuccess();
};

module.exports = Firestore;

