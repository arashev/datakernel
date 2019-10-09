import React from "react";
import path from 'path';
import {withStyles} from '@material-ui/core';
import Button from '@material-ui/core/Button';
import Dialog from '../Dialog/Dialog'
import DialogActions from '@material-ui/core/DialogActions';
import DialogContent from '@material-ui/core/DialogContent';
import DialogTitle from '@material-ui/core/DialogTitle';
import {withSnackbar} from "notistack";
import DialogContentText from "@material-ui/core/DialogContentText";
import deleteDocumentStyles from "./deleteDocumentStyles";
import {getInstance} from "global-apps-common";
import DocumentsService from "../../modules/documents/DocumentsService";
import {withRouter} from "react-router-dom";

function DeleteDocumentDialogView({classes, onClose, onDelete}) {
  return (
    <Dialog onClose={onClose}>
      <DialogTitle>Delete document</DialogTitle>
      <DialogContent>
        <DialogContentText>
          Are you sure you want to delete document?
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
          color={"primary"}
          variant={"contained"}
          onClick={() => onDelete()}
        >
          Yes
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function DeleteDocumentDialog({classes, documentId, onClose, history, match, enqueueSnackbar, closeSnackbar}) {
  const documentsService = getInstance(DocumentsService);

  const props = {
    classes,
    onClose,

    onDelete() {
      enqueueSnackbar('Deleting...');
      onClose();
      return documentsService.deleteDocument(documentId)
        .then((documentKey) => {
          const {documentId} = match.params;
          if (documentKey === documentId) {
            history.push(path.join('/document', ''));
          }
          setTimeout(() => closeSnackbar(), 1000);
        })
        .catch(error => {
          enqueueSnackbar(error.message, {
            variant: 'error'
          })
        });
    }
  };

  return <DeleteDocumentDialogView {...props}/>
}

export default withRouter(
  withSnackbar(
    withStyles(deleteDocumentStyles)(DeleteDocumentDialog)
  )
);
