import React from 'react';
import {withSnackbar} from 'notistack';
import {withStyles} from '@material-ui/core';
import Button from '@material-ui/core/Button';
import DialogActions from '@material-ui/core/DialogActions';
import DialogContent from '@material-ui/core/DialogContent';
import DialogTitle from '@material-ui/core/DialogTitle';
import DialogContentText from '@material-ui/core/DialogContentText';
import noteDialogsStyles from '../CreateNoteDialog/noteDialogsStyles';
import Dialog from '../Dialog/Dialog'
import {withRouter} from "react-router-dom";
import {getInstance} from "global-apps-common";
import NotesService from "../../modules/notes/NotesService";

function DeleteNoteDialogView({classes, onClose, onDelete}) {
  return (
    <Dialog onClose={onClose}>
      <form>
        <DialogTitle>
          Delete note
        </DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to delete note?
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button
            className={classes.actionButton}
            onClick={onClose}
          >
            No
          </Button>
          <Button
            className={classes.actionButton}
            color="primary"
            variant="contained"
            onClick={onDelete}
          >
            Yes
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function DeleteNoteDialog({classes, match, history, onClose, enqueueSnackbar, currentNoteId}) {
  const notesService = getInstance(NotesService);

  const props = {
    classes,
    onClose,

    onDelete() {
      return notesService.deleteNote(currentNoteId)
        .then(() => {
          const {noteId} = match.params;
          onClose();
          if (currentNoteId === noteId) {
            history.push('/note/');
          }
        })
        .catch(err => {
          enqueueSnackbar(err.message, {
            variant: 'error'
          });
        })
    }
  };

  return <DeleteNoteDialogView {...props}/>
}

export default withRouter(
  withSnackbar(
    withStyles(noteDialogsStyles)(DeleteNoteDialog)
  )
);