package com.rho.db;

import com.rho.db.DBException;
import com.rho.db.IDBResult;
import com.rho.db.IDBStorage;
import com.xruby.runtime.builtin.ObjectFactory;
import com.xruby.runtime.lang.RubyValue;
import org.hsqldb.*;
import org.hsqldb.persist.*;

public class HsqlDBStorage implements IDBStorage, Session.IDeleteCallback{

	private Session m_dbSess;
	private FileUtilBB m_fs;
	private IDBCallback m_dbCallback;
	private HsqlDBRowResult m_rowResult = new HsqlDBRowResult();
	
	public HsqlDBStorage(){
		m_fs = FileUtilBB.getDefaultInstance(); 
	}
	
	public void deleteAllFiles(String strPath)throws Exception
	{
		String strDbName = getNameNoExt(strPath);
		m_fs.delete(strDbName + ".data");
		m_fs.delete(strDbName + ".script");
		m_fs.delete(strDbName + ".script.new");
		m_fs.delete(strDbName + ".journal");
	}
	
	private String getNameNoExt(String strPath){
		int nDot = strPath.lastIndexOf('.');
		String strDbName = "";
		if ( nDot > 0 )
			strDbName = strPath.substring(0, nDot);
		else
			strDbName = strPath;
		
		return strDbName;
	}
	
	public void open(String strPath, String strSqlScript) throws DBException 
	{
		try{
			String strDbName = getNameNoExt(strPath);
			
			HsqlProperties props = new HsqlProperties();
			props.setProperty(HsqlDatabaseProperties.hsqldb_default_table_type, "cached");
	
			m_dbSess = DatabaseManager.newSession(DatabaseURL.S_FILE, strDbName, "SA", "", props);
			m_dbSess.setDeleteCallback(this);
			
			if ( !m_fs.exists(strDbName + ".data") )
			{
				m_dbSess.sqlExecuteDirectNoPreChecks(strSqlScript);
				m_dbSess.commitSchema();
			}
		}catch(HsqlException exc ){
			throw new DBException(exc);
		}
	}

/*    static class HsqlDeleteTrigger implements org.hsqldb.Trigger {

        public void fire(int i, String name, String table, Object[] row1,
                         Object[] row2) {
            throw new RuntimeException("Missing Trigger class!");
        }
    }
*/ 
	
	public void onDeleteRow(Table table, Row row) {
		if ( m_dbCallback == null )
			return;
		
		m_rowResult.init(table, row);
		m_dbCallback.OnDeleteFromTable(table.getName().name, m_rowResult );
	}

	public void setDbCallback(IDBCallback callback)
	{
		m_dbCallback = callback;
		if ( m_dbSess != null )
			m_dbSess.setDeleteCallback(this);
		
//		m_dbSess.sqlExecuteDirectNoPreChecks(
//			"CREATE TRIGGER rhodeleteTrigger BEFORE DELETE ON object_values FOR EACH ROW QUEUE 0 CALL \"com.rho.HsqlDBStorage.HsqlDeleteTrigger\"");
//		m_dbSess.commitSchema();
	}
	
	public void close() throws DBException {
		try{
			if ( m_dbSess != null )
				m_dbSess.getDatabase().close(Database.CLOSEMODE_NORMAL);
		}catch(HsqlException exc ){
			throw new DBException(exc);
		}
	}

	public IDBResult createResult() {
		return new HsqlDBResult();
	}

	public IDBResult executeSQL(String strStatement, Object[] values)
			throws DBException {
		
		try {
			if ( m_dbSess == null )
				throw new RuntimeException("executeSQL: m_dbSess == null");
			
			CompiledStatement st = m_dbSess.compiledStatementManager.compile(m_dbSess, strStatement);
			Result res = m_dbSess.sqlExecuteCompiledNoPreChecksSafe(st, values);
			
			if ( m_dbSess.isAutoCommit() )
				m_dbSess.commit();
			
			return new HsqlDBResult(res);
		}catch(Throwable exc ){
			throw new DBException(exc);
		}
	}
	
	public void startTransaction()throws DBException
	{
		m_dbSess.setAutoCommit(false);
	}
	
	public void commit()throws DBException
	{
		m_dbSess.setAutoCommit(true);
	}
	
}
