package aosme;

public enum MessageType {
    REQUEST ( (byte) 0),
    TOKEN   ( (byte) 1),
    CSENTER ( (byte) 2),
    CSEXIT  ( (byte) 3);
    
    byte code;
    
    private MessageType(byte code) {
        this.code = code;
    }
    
    public byte toCode(MessageType mt) {
        return code;
    }
    
    public MessageType fromCode(byte code) throws Exception {
        switch (code) {
            case 0:  return REQUEST;
            case 1:  return TOKEN;
            case 2:  return CSENTER;
            case 3:  return CSEXIT;
            default: throw new Exception("Bad MessageType code value.");
        }
    }
}
