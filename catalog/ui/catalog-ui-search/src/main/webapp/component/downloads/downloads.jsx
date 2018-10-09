/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/
import * as React from "react";
import { hot } from "react-hot-loader";

type Props = {};

const Empty = () => {
    return <div className="is-medium-font queued-download">
        Nothing Queued
    </div>
}

const RefocusToDownload = () => {
    return <div className="is-medium-font queued-download refocus">
        <span class="fa fa-exclamation-triangle"></span>Refocus on the application to continue processing downloads
    </div>
}

const Processing = (props) => {
    const { processing } = props;
    return <div className="is-medium-font queued-download">
        {processing}
    </div>
}

const Download = (props) => {
    const {filename} = props;
    return <div className="is-medium-font queued-download">
     {filename}
    </div>
}

const render = props => {
    const { downloads, processing, autoDownload } = props;
  return <React.Fragment>
      <div className="is-large-font is-header">Queued Downloads</div>
      <div className="is-divider"></div>
      {processing !== undefined && autoDownload === false ? <RefocusToDownload /> : ''}
      {downloads.length === 0 && processing === undefined ? <Empty /> : ''}
      {processing === undefined ? '' : <Processing processing={processing} />}
      {downloads.map(download => <Download key={download.id} filename={download.filename} />)}
  </React.Fragment>;
};

export default hot(module)(render);
