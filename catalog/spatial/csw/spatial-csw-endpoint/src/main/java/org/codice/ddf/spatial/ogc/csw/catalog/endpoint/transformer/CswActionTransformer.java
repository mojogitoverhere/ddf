package org.codice.ddf.spatial.ogc.csw.catalog.endpoint.transformer;

import java.util.List;
import javax.xml.namespace.QName;
import org.codice.ddf.spatial.ogc.csw.catalog.actions.DeleteAction;
import org.codice.ddf.spatial.ogc.csw.catalog.actions.UpdateAction;
import org.codice.ddf.spatial.ogc.csw.catalog.common.transaction.InsertAction;

public interface CswActionTransformer {

  UpdateAction transform(UpdateAction updateAction);

  DeleteAction transform(DeleteAction deleteAction);

  InsertAction transform(InsertAction insertAction);

  List<String> getTypeNames();

  List<QName> getQNames();
}
