package org.codice.ddf.spatial.ogc.csw.catalog.common.transformer;

import org.codice.ddf.spatial.ogc.csw.catalog.common.transaction.DeleteAction;
import org.codice.ddf.spatial.ogc.csw.catalog.common.transaction.InsertAction;
import org.codice.ddf.spatial.ogc.csw.catalog.common.transaction.UpdateAction;

public interface CswActionTransformer {

  UpdateAction transform(UpdateAction updateAction);

  DeleteAction transform(DeleteAction deleteAction);

  InsertAction transform(InsertAction insertAction);
}
