const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

exports.onMessageSent = onDocumentCreated(
  "messages/{messageId}",
  async (event) => {
    const msg = event.data.data();
    if (!msg) return;

    const recipientUid = msg.recipientUid;
    const senderEdPub = msg.sender;
    const body = msg.body || "";
    const senderName = msg.senderName || "Unknown";

    if (!recipientUid) {
      console.log("No recipientUid, skipping push");
      return;
    }

    try {
      const userDoc = await db.collection("users").doc(recipientUid).get();
      const fcmToken = userDoc.data()?.fcmToken;
      if (!fcmToken) {
        console.log(`No FCM token for ${recipientUid}`);
        return;
      }

      await admin.messaging().sendEachForMulticast({
        tokens: [fcmToken],
        data: {
          type: "message",
          senderEdPub: senderEdPub,
          senderName: senderName,
          body: body,
          conversationId: senderEdPub,
          payload: body,
          title: senderName,
        },
        notification: {
          title: senderName,
          body: body.length > 100 ? body.substring(0, 100) + "..." : body,
        },
        android: {
          priority: "high",
          notification: {
            channelId: "squelch_messages",
            clickAction: "OPEN_CONVERSATION",
          },
        },
      });

      console.log(`Push sent to ${recipientUid}`);
    } catch (err) {
      console.error("Push send failed:", err);
    }
  }
);

exports.onDeliveryAck = onDocumentCreated(
  "delivery_acks/{ackId}",
  async (event) => {
    const ack = event.data.data();
    if (!ack) return;

    const senderUid = ack.senderUid;
    const msgId = ack.msgId;

    try {
      const userDoc = await db.collection("users").doc(senderUid).get();
      const fcmToken = userDoc.data()?.fcmToken;
      if (!fcmToken) return;

      await admin.messaging().sendEachForMulticast({
        tokens: [fcmToken],
        data: { type: "delivery_ack", msgId: msgId },
      });
      console.log(`Delivery ack sent to ${senderUid} for ${msgId}`);
    } catch (err) {
      console.error("Delivery ack failed:", err);
    }
  }
);
