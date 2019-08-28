import React from "react";
import path from 'path';
import {withStyles} from '@material-ui/core';
import {Avatar} from "global-apps-common";
import ListItemText from "@material-ui/core/ListItemText";
import ListItem from "@material-ui/core/ListItem";
import roomItemStyles from "./roomItemStyles";
import ContactMenu from "../ContactMenu/ContactMenu";
import AddContactDialog from "../AddContactDialog/AddContactDialog";
import {Link, withRouter} from "react-router-dom";
import IconButton from "@material-ui/core/IconButton";
import DeleteIcon from "@material-ui/icons/Delete";
import {getRoomName} from "../../common/utils";
import {withSnackbar} from "notistack";

class RoomItem extends React.Component {
  state = {
    showAddContactDialog: false
  };

  static defaultProps = {
    showDeleteButton: false,
    active: true
  };

  onClickAddContact() {
    this.setState({
      showAddContactDialog: true
    });
  }

  onRemoveContact(room) {
    const publicKey = room.participants.find(publicKey => publicKey !== this.props.publicKey);
    const name = this.props.contacts.get(publicKey).name;
    if (!window.navigator.onLine) {
      this.props.enqueueSnackbar('Deleting...');
    }
    this.props.onRemoveContact(publicKey, name)
      .catch((err) => {
        this.props.enqueueSnackbar(err.message, {
          variant: 'error'
        });
      })
  }

  closeAddDialog = () => {
    this.setState({
      showAddContactDialog: false
    });
  };

  checkContactExists(room) {
    if (!room.dialog) {
      return false;
    }
    const participantPublicKey = room.participants.find(participantPublicKey => {
      return participantPublicKey !== this.props.publicKey;
    });
    return !this.props.contacts.has(participantPublicKey);
  }

  getContactId(room) {
    return room.participants
      .find(participantPublicKey => participantPublicKey !== this.props.publicKey);
  }

  render() {
    const {classes, room, roomId} = this.props;
    const roomURL = path.join('/room', roomId || '');
    const roomName = getRoomName(room.participants, this.props.names, this.props.publicKey);

    return (
      <>
        <ListItem
          onClick={this.props.onClick}
          className={classes.listItem}
          button
          selected={roomId === this.props.match.params.roomId && this.props.active}
        >
          <Link
            to={roomURL}
            className={classes.link}
          >
            <Avatar
              selected={!this.props.selected}
              name={roomName}
            />
            <ListItemText
              primary={roomName}
              className={classes.itemText}
              classes={{primary: classes.itemTextPrimary}}
            />
          </Link>
          {this.checkContactExists(room) && !this.props.showDeleteButton && (
            <ContactMenu onAddContact={this.onClickAddContact.bind(this)}/>
          )}
          {this.props.showDeleteButton && (
            <IconButton className={classes.deleteIcon}>
              <DeleteIcon
                onClick={this.onRemoveContact.bind(this, room)}
                fontSize="medium"
              />
            </IconButton>
          )}
        </ListItem>
        <AddContactDialog
          open={this.state.showAddContactDialog}
          onClose={this.closeAddDialog}
          contactPublicKey={this.getContactId(room)}
          publicKey={this.props.publicKey}
          onAddContact={this.props.onAddContact}
        />
      </>
    )
  }
}

export default withSnackbar(withRouter(withStyles(roomItemStyles)(RoomItem)));

