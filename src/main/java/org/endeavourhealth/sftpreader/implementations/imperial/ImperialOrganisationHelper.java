package org.endeavourhealth.sftpreader.implementations.imperial;

import org.endeavourhealth.sftpreader.implementations.SftpOrganisationHelper;
import org.endeavourhealth.sftpreader.implementations.imperial.utility.ImperialConstants;
import org.endeavourhealth.sftpreader.model.DataLayerI;

public class ImperialOrganisationHelper extends SftpOrganisationHelper {

    @Override
    public String findOrganisationNameFromOdsCode(DataLayerI db, String odsCode) throws Exception {

       if (odsCode.equalsIgnoreCase(ImperialConstants.ICH_ODS_CODE)) {
            return "IMPERIAL COLLEGE HEALTHCARE NHS TRUST";

        } else {
            return "<UNKNOWN ORGANISATION>";
        }
    }
}
