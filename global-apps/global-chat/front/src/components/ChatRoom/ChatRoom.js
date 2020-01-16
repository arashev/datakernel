import React, {useMemo} from 'react';
import {withStyles} from '@material-ui/core';
import chatStyles from './chatRoomStyles';
import Messages from '../Messages/Messages';
import MessageForm from '../MessageForm/MessageForm';
import ChatRoomService from "../../modules/chatroom/ChatRoomService";
import {RegisterDependency, initService, useSnackbar} from "global-apps-common";

function ChatRoom({roomId, publicKey, classes}) {
  const {showSnackbar} = useSnackbar();
  const chatRoomService = useMemo(() => (
    ChatRoomService.createFrom(roomId, publicKey)
  ), [roomId, publicKey]);

  initService(chatRoomService, err => showSnackbar(err.message, 'error'));

  return (
    <RegisterDependency name={ChatRoomService} value={chatRoomService}>
      <div className={classes.root}>
        <Messages publicKey={publicKey}/>
        <MessageForm/>
      </div>
    </RegisterDependency>
  );
}

export default withStyles(chatStyles)(ChatRoom);
