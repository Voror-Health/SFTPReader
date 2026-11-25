package org.endeavourhealth.sftpreader.implementations.imperial.utility;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.QuoteMode;

public class ImperialConstants {

    public static final String CDE_DATE_TIME_FORMAT = "dd/MM/yyyy hh:mm:ss";
    public static final String CDE_BULK_DATE_TIME_FORMAT = "yyyy-MM-dd hh:mm:ss"; //datetime format used on the manual imperial bulks
    public static final String CDS_DATE_TIME_FORMAT = "yyyyMMdd HHmmss";


    //CDS files use a fixed width column format, so this is only applicable to CDE files
    public static final CSVFormat CDE_CSV_FORMAT = CSVFormat.DEFAULT
            .withHeader()
            .withDelimiter('|')
            .withEscape((Character)null)
            .withQuote((Character)null)
            .withQuoteMode(QuoteMode.MINIMAL); //ideally want Quote Mdde NONE, but validation in the library means we need to use this


    public static final String FILE_ID_CDE_PPATI = "PPATI";
    public static final String FILE_ID_CDE_CLEVE = "CLEVE";
    public static final String FILE_ID_CDE_ENCNT = "ENCNT";
    public static final String FILE_ID_CDE_CVREF = "CVREF";
    public static final String ICH_ODS_CODE = "RYJ";

}
