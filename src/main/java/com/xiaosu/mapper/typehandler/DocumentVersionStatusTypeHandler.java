package com.xiaosu.mapper.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import com.xiaosu.domain.DocumentVersionStatus;

@MappedTypes(DocumentVersionStatus.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class DocumentVersionStatusTypeHandler extends BaseTypeHandler<DocumentVersionStatus> {

    @Override
    public void setNonNullParameter(
            PreparedStatement statement, int index, DocumentVersionStatus value, JdbcType jdbcType)
            throws SQLException {
        statement.setString(index, value.databaseValue());
    }

    @Override
    public DocumentVersionStatus getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return fromString(resultSet.getString(columnName));
    }

    @Override
    public DocumentVersionStatus getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return fromString(resultSet.getString(columnIndex));
    }

    @Override
    public DocumentVersionStatus getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return fromString(statement.getString(columnIndex));
    }

    private static DocumentVersionStatus fromString(String value) {
        return value == null ? null : DocumentVersionStatus.fromDatabase(value);
    }
}
