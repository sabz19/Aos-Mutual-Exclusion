package aosme;

public enum MessageType {
    REQUEST   ( (byte) 0),  // size: 1 byte:  REQUEST
    TOKEN     ( (byte) 1),  // size: 5 bytes: TOKEN, timestamp (int)
    CSREQUEST ( (byte) 2),  // size: 1 byte:  CSREQUEST
    CSGRANT   ( (byte) 3),  // size: 1 byte:  CSGRANT
    CSRETURN  ( (byte) 4),  // size: 1 byte:  CSRETURN
    APPDONE   ( (byte) 5),  // size: 1 byte:  APPDONE
    NODEDONE  ( (byte) 6),  // size: 2 bytes: NODEDONE, id (byte)
    NETBUILD   ( (byte) 7), // size: 1 byte:  NETBUILD
    NETSTART  ( (byte) 8);  // size: 1 byte:  NETSTART
    
    byte code;
    
    private MessageType(byte code) {
        this.code = code;
    }
    
    public byte toCode() {
        return code;
    }
    
    public static MessageType fromCode(byte code) throws Exception {
        switch (code) {
            case 0:  return REQUEST;
            case 1:  return TOKEN;
            case 2:  return CSREQUEST;
            case 3:  return CSGRANT;
            case 4:  return CSRETURN;
            case 5:  return APPDONE;
            case 6:  return NODEDONE;
            case 7:  return NETBUILD;
            case 8:  return NETSTART;
            default: throw new Exception("Bad MessageType code value.");
        }
    }
}
