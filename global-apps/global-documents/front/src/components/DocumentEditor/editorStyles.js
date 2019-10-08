const editorStyles = theme => ({
  editor: {
    fontSize: theme.typography.h6.fontSize,
    fontFamily: 'Roboto',
    display: 'flex',
    flexGrow: 1,
    outline: 0,
    width: '100%',
    height: '100%',
    resize: 'none',
    borderStyle: 'none',
    overflow: 'hidden',
    '&:hover': {
      overflow: 'overlay'
    }
  },
  paper: {
    flexGrow: 1,
    margin: theme.spacing(3),
    marginTop: theme.spacing(11),
    padding: theme.spacing(3),
    paddingRight: theme.spacing(2)
  }
});

export default editorStyles;