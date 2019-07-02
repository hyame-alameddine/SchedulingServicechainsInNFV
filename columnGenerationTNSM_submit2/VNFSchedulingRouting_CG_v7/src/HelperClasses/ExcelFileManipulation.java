/**
 * This uses the jxl library that need to be imported 
 * http://www.vogella.com/tutorials/JavaExcel/article.html
 * Make sure that the write and close are done at the end after adding everything to be written to the sheet
 */
package HelperClasses;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.demo.ReadWrite;
import jxl.read.biff.BiffException;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;

public class ExcelFileManipulation {

	/**
	 * This function creates a new workbook based on the provided file path
	 * @param filePath path to the workbook to create
	 * @return writable workbook
	 */
	public WritableWorkbook createWorkbook (String filePath)
	{
		WritableWorkbook workbook = null;
		
		//create a file
		File file = new File(filePath);
		
		WorkbookSettings wbSettings = new WorkbookSettings();
        wbSettings.setLocale(new Locale("en", "EN"));
        
        //create the excel workbook        
		try {
			workbook = Workbook.createWorkbook(file, wbSettings);
			
		} catch (IOException e) {			
			e.printStackTrace();
		} 
        
        return workbook;
	}
	
	
	/**
	 * 
	 * This function works only if  the excel sheet created contains some data
	 * This function returns a writable copy of an existing workbook excel file to modify
	 * @param filePath file path to the excel file
	 * @return workbook to modify
	 */
	public WritableWorkbook getModifiableWorkbook( String filePath)
	{
		File inputWorkbook = new File(filePath);
		
        Workbook workbookToModify=null;
        WritableWorkbook modifiedWorkbook = null;
		try {
			//readable copy of the excel
			workbookToModify = Workbook.getWorkbook(inputWorkbook);
			//get a writable copy
	        modifiedWorkbook = Workbook.createWorkbook(inputWorkbook, workbookToModify);
	       // workbookToModify.close();
	   
		} catch (BiffException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        return modifiedWorkbook;
	}
	

	
}
