package io.lastwill.eventscan.services.xls;

import io.lastwill.eventscan.model.TokenInfo;
import io.lastwill.eventscan.repositories.TokenEntryRepository;
import io.lastwill.eventscan.services.RandomMd5Generator;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
@Slf4j
public class FillFromExcel {
    private final String weight = "Weight, gram";
    private final String country = "Country";
    private final String certifiedAssayer = "Certified Assayer";
    private final String productionDate = "Production Date";
    private final String purchaseDate = "Purchase Date";
    private final String secretCode = "Secret Code";
    private final String publicCode = "Public Code";
    private final String goldPrice = "Gold price";
    private final String ducValue = "DUC value";
    @Value("${io.lastwill.eventscan.open-file-name}")
    private String openFile;
    @Value("${io.lastwill.eventscan.save-file-name}")
    private String saveFile;
    Map<String, Integer> rowByType = new HashMap<>();

    private final int stopGenerate = 100;
    @Autowired
    private RandomMd5Generator generator;
    @Autowired
    private TokenEntryRepository tokenRepository;
    String homePath = System.getProperty("user.dir");

    @PostConstruct
    public void init() throws IOException {
        addSecretCode();
        saveIntoDb();
    }

    private void addSecretCode() throws IOException {
        File file = new File(homePath + File.separator + openFile);
        if (!file.exists()) {
            log.info("File {} for configure secret code is not exist", homePath + File.separator + openFile);
            return;
        }
        // Read XSL file
        FileInputStream inputStream = new FileInputStream(file);

        // Get the workbook instance for XLS file
        HSSFWorkbook workbook = new HSSFWorkbook(inputStream);

        // Get first sheet from the workbook
        HSSFSheet sheet = workbook.getSheetAt(0);
        Iterator<Row> rowIterator = sheet.iterator();
        int rows = sheet.getLastRowNum();
        Map<String, String> uniqueCodes = generateUnique(rows, 0);
        if (uniqueCodes.isEmpty()) {
            log.warn("Can't generate {} unique codes!", rows);
            return;
        }
        Iterator<Cell> cellIterator = rowIterator.next().cellIterator();
        while (cellIterator.hasNext()) {
            Cell cell = cellIterator.next();
            String cellName = cell.getStringCellValue();
            switch (cellName) {
                case secretCode: {
                    rowByType.put(secretCode, cell.getColumnIndex());
                    break;
                }
                case weight: {
                    rowByType.put(weight, cell.getColumnIndex());
                    break;
                }
                case country: {
                    rowByType.put(country, cell.getColumnIndex());
                    break;
                }
                case certifiedAssayer: {
                    rowByType.put(certifiedAssayer, cell.getColumnIndex());
                    break;
                }
                case purchaseDate: {
                    rowByType.put(purchaseDate, cell.getColumnIndex());
                    break;
                }
                case productionDate: {
                    rowByType.put(productionDate, cell.getColumnIndex());
                    break;
                }
                case goldPrice: {
                    rowByType.put(goldPrice, cell.getColumnIndex());
                    break;
                }
                case ducValue: {
                    rowByType.put(ducValue, cell.getColumnIndex());
                    break;
                }
                case publicCode: {
                    rowByType.put(publicCode, cell.getColumnIndex());
                    break;
                }
                default:
                    break;
            }
        }
        if (rowByType.size() != 9) {
            log.warn("Find only fields {}", rowByType.size());
            return;
        }
        Set<Map.Entry<String, String>> entrySet = uniqueCodes.entrySet();
        Iterator<Map.Entry<String, String>> codeIterator = entrySet.iterator();
        for (int i = 1; i <= rows; i++) {
            if (!codeIterator.hasNext()) {
                return;
            }
            Map.Entry<String, String> entry = codeIterator.next();
            String code = generator.convertToUserFriendlyFormat(entry.getKey());
            String pubCode = generator.convertToUserFriendlyFormat(entry.getValue());

            int secretCell = rowByType.get(this.secretCode);
            int publicCell = rowByType.get(this.publicCode);
            Row row = sheet.getRow(i);

            Cell cell = row.createCell(secretCell, CellType.STRING);
            cell.setCellValue(code);

            cell = row.createCell(publicCell, CellType.STRING);
            cell.setCellValue(pubCode);

        }
        log.info("Secret code successfully generate");
        inputStream.close();
        // Write File
        FileOutputStream out = new FileOutputStream(file);
        workbook.write(out);
        out.close();
        log.info("File with source code save");
    }

    private void saveIntoDb() throws IOException {
        File file = new File(homePath + File.separator + openFile);
        if (!file.exists()) {
            log.info("File for saveDB is not exist");
            return;
        }
        // Read XSL file
        FileInputStream inputStream = new FileInputStream(file);

        // Get the workbook instance for XLS file
        HSSFWorkbook workbook = new HSSFWorkbook(inputStream);

        // Get first sheet from the workbook
        HSSFSheet sheet = workbook.getSheetAt(0);
        int rows = sheet.getLastRowNum();
        List<TokenInfo> tokens = new ArrayList<>();
        for (int i = 1; i <= rows; i++) {
            HSSFRow row = sheet.getRow(i);
            String tSecretCode = row.getCell(rowByType.get(secretCode)).getStringCellValue();
            tSecretCode = generator.convertToDBFormat(tSecretCode);
            String tPublicCode = row.getCell(rowByType.get(publicCode)).getStringCellValue();
            tPublicCode = generator.convertToDBFormat(tPublicCode);
            Integer tTokenType = (int) row.getCell(rowByType.get(weight)).getNumericCellValue();
            String tAssayer = row.getCell(rowByType.get(certifiedAssayer)).getStringCellValue();
            String tCountry = row.getCell(rowByType.get(country)).getStringCellValue();
            String tPurchaseDate = row.getCell(rowByType.get(purchaseDate)).getStringCellValue();
            String tProductionDate = row.getCell(rowByType.get(productionDate)).getStringCellValue();
            BigDecimal tDucValueInPercent = BigDecimal.valueOf(row.getCell(rowByType.get(ducValue)).getNumericCellValue());
            BigDecimal tDucValue = tDucValueInPercent.divide(new BigDecimal(100));
            BigDecimal tGoldPrice = BigDecimal.valueOf(row.getCell(rowByType.get(goldPrice)).getNumericCellValue());

            tokens.add(new TokenInfo(tSecretCode, tPublicCode, tTokenType, false, tAssayer, tCountry, tDucValue, tGoldPrice, tPurchaseDate, tProductionDate));
        }
        tokenRepository.save(tokens);
        log.info("All new Token Info entry successfully save into DB");
        inputStream.close();
        // Write File
        String newDate = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
        String saveFileName = homePath + File.separator + newDate + File.separator + saveFile;
        new File(homePath + File.separator + newDate).mkdirs();
        FileOutputStream out = new FileOutputStream(saveFileName);
        workbook.write(out);
        out.close();
        //Delete old File
        File fileForDelete = new File(homePath + File.separator + openFile);
        fileForDelete.delete();
        log.info("New File with Secret code save into {}", saveFileName);
        log.info("Old file was  dropped {}", homePath + File.separator + openFile);
    }

    private Map<String, String> generateUnique(int rows, int count) {
        Map<String, String> result = new HashMap<>();
        if (count > stopGenerate) {
            log.warn("Can't generate unique code more than {} times", stopGenerate);
            return result;
        }
        Map<String, String> codes = generator.generateMoreMd5Random(rows);
        List<TokenInfo> repeatsPrivate = tokenRepository.findAllBySecretCode(codes.keySet());
        List<TokenInfo> repeatsPublic = tokenRepository.findAllByPublicCode(codes.values());
        if (repeatsPrivate != null && (repeatsPrivate.size() > 0 || repeatsPublic.size() > 0)) {
            result = this.generateUnique(rows, ++count);
        } else {
            result = codes;
        }
        log.info("Secret code successfully generated");
        return result;
    }
}