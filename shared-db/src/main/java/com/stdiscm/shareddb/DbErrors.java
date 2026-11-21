package com.stdiscm.shareddb;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;

/**
 * Classifies database failures by walking the exception's causes (Spring and Hibernate wrap the
 * driver's SQLException several levels deep).
 */
public final class DbErrors {

    /** PostgreSQL read_only_sql_transaction: a write reached the read-only replica. */
    public static final String READ_ONLY_SQL_TRANSACTION = "25006";

    private static final int MAX_DEPTH = 25;

    private DbErrors() { }

    public static boolean isReadOnlyReplica(Throwable t) {
        for (Throwable c : chain(t)) {
            if (c instanceof SQLException se && READ_ONLY_SQL_TRANSACTION.equals(se.getSQLState())) return true;
        }
        return false;
    }

    /** No connection could be made or kept: the database (or its host) is down or unreachable. */
    public static boolean isConnectionFailure(Throwable t) {
        for (Throwable c : chain(t)) {
            if (c instanceof SQLTransientConnectionException || c instanceof SQLNonTransientConnectionException
                    || c instanceof ConnectException || c instanceof SocketTimeoutException) {
                return true;
            }
            if (c instanceof SQLException se && se.getSQLState() != null) {
                String s = se.getSQLState();
                // 08xxx connection exception, 57P01-03 server shutting down / starting up, 53300 too many connections
                if (s.startsWith("08") || s.startsWith("57P") || s.equals("53300")) return true;
            }
        }
        return false;
    }

    /** The connection itself broke while in use (server shut down, crashed or link lost): 08xxx or 57Pxx. */
    public static boolean isBrokenConnection(Throwable t) {
        for (Throwable c : chain(t)) {
            if (c instanceof SQLException se && se.getSQLState() != null
                    && (se.getSQLState().startsWith("08") || se.getSQLState().startsWith("57P"))) {
                return true;
            }
        }
        return false;
    }

    private static java.util.List<Throwable> chain(Throwable t) {
        java.util.List<Throwable> out = new java.util.ArrayList<>();
        java.util.ArrayDeque<Throwable> todo = new java.util.ArrayDeque<>();
        if (t != null) todo.add(t);
        while (!todo.isEmpty() && out.size() < MAX_DEPTH) {
            Throwable c = todo.poll();
            if (out.contains(c)) continue;
            out.add(c);
            if (c.getCause() != null) todo.add(c.getCause());
            if (c instanceof SQLException se && se.getNextException() != null) todo.add(se.getNextException());
            for (Throwable s : c.getSuppressed()) todo.add(s);
        }
        return out;
    }
}
