import React, {useEffect} from 'react';
import qs from 'query-string';
import {connectService} from '../service/connectService';
import {withRouter} from 'react-router-dom';
import {AuthContext} from "./AuthContext";
import AfterAuthRedirect from "./AfterAuthRedirect";

function OAuthCallbackComponent({location, authByPrivateKey}) {
  const params = qs.parse(location.search);

  useEffect(() => {
    if (params.privateKey) {
      authByPrivateKey(params.privateKey);
    }
  });

  return <AfterAuthRedirect/>;
}

const OAuthCallback = connectService(
  AuthContext, (state, accountService) => ({
    authByPrivateKey(privateKey) {
      accountService.authByPrivateKey(privateKey);
    }
  })
)(
  withRouter(OAuthCallbackComponent)
);

export {OAuthCallback};
