import Popover from '@material-ui/core/Popover';
import MenuList from '@material-ui/core/MenuList';
import MenuItem from '@material-ui/core/MenuItem';
import ListItemIcon from '@material-ui/core/ListItemIcon';
import ListItemText from '@material-ui/core/ListItemText';
import DeleteIcon from '@material-ui/icons/DeleteForeverOutlined';

const ContextMenu = props => {
  return (
    < Popover;
  open = {props.style.open};
  anchorReference = "anchorPosition";
  anchorPosition = {;
  {
    props.style.top, left;
  :
    props.style.left
  }
}
  anchorOrigin = {;
  {
    'top',
      horizontal;
  :
    'left',
  }
}
  transformOrigin = {;
  {
    'top',
      horizontal;
  :
    'left',
  }
}
>
<
  MenuList >
  < MenuItem;
  button;
  onClick = {props.deleteHandler} >
    < ListItemIcon >
    < DeleteIcon / >
    < /ListItemIcon>
    < ListItemText;
  inset;
  primary = "Delete" / >
    < /MenuItem>
    < /MenuList>
    < /Popover>;
)
};

export default ContextMenu;
