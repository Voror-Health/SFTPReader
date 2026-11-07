package org.endeavourhealth.sftpreader.implementations.nems;

import org.endeavourhealth.sftpreader.implementations.SftpOrganisationHelper;
import org.endeavourhealth.sftpreader.model.DataLayerI;

public class NemsOrganisationHelper extends SftpOrganisationHelper {
    @Override
    public String findOrganisationNameFromOdsCode(DataLayerI db, String odsCode) throws Exception {
        if (odsCode.equalsIgnoreCase("X26")) {
            return "NHS DIGITAL";

        } else {
            return "Unexpected Organisation ODS code used for NEMS service: " + odsCode;
        }
    }
}
