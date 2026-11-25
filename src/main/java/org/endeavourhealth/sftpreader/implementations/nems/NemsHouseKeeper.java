package org.endeavourhealth.sftpreader.implementations.nems;

import org.endeavourhealth.sftpreader.implementations.SftpHouseKeeper;
import org.endeavourhealth.sftpreader.model.DataLayerI;
import org.endeavourhealth.sftpreader.model.db.DbConfiguration;
import org.endeavourhealth.sftpreader.model.db.DbInstanceEds;

public class NemsHouseKeeper extends SftpHouseKeeper {
    @Override
    public void performHouseKeeping(DataLayerI db, DbInstanceEds instanceConfiguration, DbConfiguration dbConfiguration) throws Exception {
        //no house keeping necessary
    }
}
