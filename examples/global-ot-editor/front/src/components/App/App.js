import React, {Component} from 'react';
import DocumentEditor from '../DocumentEditor/DocumentEditor';
import connectService from '../../common/connectService';
import EditorContext from '../../modules/editor/EditorContext';
import './App.css';

class App extends Component {
  onInsert = (position, content) => {
    this.props.editorService.insert(position, content);
  };

  onDelete = (position, content) => {
    this.props.editorService.delete(position, content);
  };

  onReplace = (position, oldContent, newContent) => {
    this.props.editorService.replace(position, oldContent, newContent);
  };

  render() {
    if (!this.props.ready) {
      return 'Loading...';
    }

    return (
      <DocumentEditor
        value={this.props.content}
        onInsert={this.onInsert}
        onDelete={this.onDelete}
        onReplace={this.onReplace}
      />
    );
  }
}

export default connectService(EditorContext, (({content, ready}, editorService) => ({
  content,
  ready,
  editorService
})))(App);
