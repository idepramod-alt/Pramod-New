package androidx.datastore.preferences.protobuf;
@CheckReturnValue
/* loaded from: classes.dex */
interface MessageInfo {
    MessageLite getDefaultInstance();

    ProtoSyntax getSyntax();

    boolean isMessageSetWireFormat();
}
