package com.wulff.lecturesight.visca.protocol;

public class VISCA {

  public static enum MessageType {

    INQUIRY, MOVEMENT, ZOOM, CAM_COMMAND, CAM_ADMIN, NET
  }

  /**
   * Enum of error types in VISCA.
   *
   */
  public static enum ErrorType{
    SYNTAX_ERROR,CMD_BUFFER_FULL,CMD_CANCELLED,NO_SOCKET,CMD_NOT_EXECUTABLE,UNKNOWN
  }

  public static final int TERMINATOR = 0xff;
  public static final int ADR_BROADCAST = 0x88;
  public static final int ADR_CAMERA_N = 0x80;
  public static final int DATA = 0x00;

  public static final int DEFAULT_SPEED = 0x01;
  
  public static final int CMD_LENGTH = 4;

  // definition of byte sequences of VISCA messages
  public static final int[] CODE_AddressSet = {ADR_BROADCAST, 0x30, 0x01, TERMINATOR};
  public static final int[] CODE_IfClear = {ADR_CAMERA_N, 0x01, 0x00, 0x01, TERMINATOR};
  public static final int[] CODE_CommandCancel = {ADR_CAMERA_N, 0x20, TERMINATOR};
  public static final int[] CODE_CamVersion_Inq = {ADR_CAMERA_N, 0x09, 0x00, 0x02, TERMINATOR};
  public static final int[] CODE_PanTiltPos_Inq = {ADR_CAMERA_N, 0x09, 0x06, 0x12, TERMINATOR};
  public static final int[] CODE_MoveHome_Cmd = {ADR_CAMERA_N, 0x01, 0x06, 0x04, TERMINATOR};
  public static final int[] CODE_MoveAbsolute_Cmd = {ADR_CAMERA_N, 0x01, 0x06, 0x02, DATA, DATA,  DATA, DATA,  DATA, DATA,  DATA, DATA,  DATA, DATA, TERMINATOR};
  public static final int[] CODE_MoveRelative_Cmd = {ADR_CAMERA_N, 0x01, 0x06, 0x03, DATA, DATA,  DATA, DATA,  DATA, DATA,  DATA, DATA,  DATA, DATA, TERMINATOR};
  public static final int[] CODE_MoveUp_Cmd = {ADR_CAMERA_N, 0x01, 0x06, 0x01, DATA, DATA, 0x03, 0x01, TERMINATOR};
  public static final int[] CODE_MoveDown_Cmd = {ADR_CAMERA_N, 0x01, 0x06, 0x01, DATA, DATA, 0x03, 0x02, TERMINATOR};
  public static final int[] CODE_MoveLeft_Cmd = {ADR_CAMERA_N, 0x01, 0x06, 0x01, DATA, DATA, 0x01, 0x03, TERMINATOR};
  public static final int[] CODE_MoveRight_Cmd = {ADR_CAMERA_N, 0x01, 0x06, 0x01, DATA, DATA, 0x02, 0x03, TERMINATOR};
  public static final int[] CODE_MoveUpLeft_Cmd = {ADR_CAMERA_N, 0x01, 0x06, 0x01, DATA, DATA, 0x01, 0x01, TERMINATOR};
  public static final int[] CODE_MoveUpRight_Cmd = {ADR_CAMERA_N, 0x01, 0x06, 0x01, DATA, DATA, 0x02, 0x01, TERMINATOR};
  public static final int[] CODE_MoveDownLeft_Cmd = {ADR_CAMERA_N, 0x01, 0x06, 0x01, DATA, DATA, 0x01, 0x02, TERMINATOR};
  public static final int[] CODE_MoveDownRight_Cmd = {ADR_CAMERA_N, 0x01, 0x06, 0x01, DATA, DATA, 0x02, 0x02, TERMINATOR};
  public static final int[] CODE_StopMove_Cmd = {ADR_CAMERA_N, 0x01, 0x06, 0x01, DATA, DATA, 0x03, 0x03, TERMINATOR};
  public static final int[] CODE_LimitSet_Cmd = {ADR_CAMERA_N, 0x01, 0x06, 0x07, 0x00, DATA, DATA, DATA,  DATA, DATA, DATA, DATA,  DATA, DATA, TERMINATOR};
  public static final int[] CODE_LimitClear_Cmd = {ADR_CAMERA_N, 0x01, 0x06, 0x07, 0x01, DATA, 0x07, 0x0f,  0x0f, 0x0f, 0x07, 0x0f,  0x0f, 0x0f, TERMINATOR};
  public static final int[] CODE_Zoom_Cmd = {ADR_CAMERA_N, 0x01, 0x04, 0x47, DATA, DATA, DATA, DATA, TERMINATOR};
  
  // Network messages __________________________________________________________
  public static final Message NET_ADDRESS_SET = new Message(MessageType.NET, CODE_AddressSet);
  public static final Message NET_IF_CLEAR = new Message(MessageType.NET, CODE_IfClear);
  public static final Message NET_COMMAND_CANCEL = new Message(MessageType.NET, CODE_CommandCancel);

  // Inqury messages ___________________________________________________________
  public static final Message INQ_CAM_VERSION = new Message(MessageType.INQUIRY, CODE_CamVersion_Inq);
  public static final Message INQ_PAN_TILT_POS = new Message(MessageType.INQUIRY, CODE_PanTiltPos_Inq);

  // Command messages __________________________________________________________
  public static final Message CMD_MOVE_HOME = new Message(MessageType.MOVEMENT, CODE_MoveHome_Cmd);
  public static final Message CMD_MOVE_UP = new Message(MessageType.MOVEMENT, CODE_MoveUp_Cmd);
  public static final Message CMD_MOVE_DOWN = new Message(MessageType.MOVEMENT, CODE_MoveDown_Cmd);
  public static final Message CMD_MOVE_LEFT = new Message(MessageType.MOVEMENT, CODE_MoveLeft_Cmd);
  public static final Message CMD_MOVE_RIGHT = new Message(MessageType.MOVEMENT, CODE_MoveRight_Cmd);
  public static final Message CMD_MOVE_UP_LEFT = new Message(MessageType.MOVEMENT, CODE_MoveUpLeft_Cmd);
  public static final Message CMD_MOVE_UP_RIGHT = new Message(MessageType.MOVEMENT, CODE_MoveUpRight_Cmd);
  public static final Message CMD_MOVE_DOWN_LEFT = new Message(MessageType.MOVEMENT, CODE_MoveDownLeft_Cmd);
  public static final Message CMD_MOVE_DOWN_RIGHT = new Message(MessageType.MOVEMENT, CODE_MoveDownRight_Cmd);
  public static final Message CMD_MOVE_ABSOULTE = new Message(MessageType.MOVEMENT, CODE_MoveAbsolute_Cmd);
  public static final Message CMD_MOVE_RELATIVE = new Message(MessageType.MOVEMENT, CODE_MoveRelative_Cmd);
  public static final Message CMD_STOP_MOVE = new Message(MessageType.MOVEMENT, CODE_StopMove_Cmd);
  public static final Message CMD_LIMIT_SET = new Message(MessageType.CAM_COMMAND, CODE_LimitSet_Cmd);
  public static final Message CMD_LIMIT_CLEAR = new Message(MessageType.CAM_COMMAND, CODE_LimitClear_Cmd);
  public static final Message CMD_ZOOM = new Message(MessageType.ZOOM, CODE_Zoom_Cmd);
}
