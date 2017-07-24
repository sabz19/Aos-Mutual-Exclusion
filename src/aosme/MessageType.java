package aosme;

public enum MessageType {
    REQUEST ( (byte) 0),    // size: 1 byte:  REQUEST
    TOKEN   ( (byte) 1),    // size: 5 bytes: TOKEN, timestamp (int)
    CSREQUEST ( (byte) 2),  // size: 1 byte:  CSREQUEST
    CSGRANT ( (byte) 3),    // size: 1 byte:  CSGRANT
    CSRETURN  ( (byte) 4);  // size: 1 byte:  CSRETURN
    
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
            default: throw new Exception("Bad MessageType code value.");
        }
    }
}
