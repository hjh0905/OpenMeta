package info.openmeta.framework.orm.jdbc;

import info.openmeta.framework.base.utils.Cast;
import info.openmeta.framework.orm.annotation.LogSql;
import info.openmeta.framework.orm.jdbc.database.SqlParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JDBC method proxy class.
 * Controlling the scope of JDBC methods, must use parameterized methods, and SQL log output.
 */
@Slf4j
@Repository
public class JdbcProxy {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Insert and return id.
     *
     * @param sqlParams SQL parameters
     */
    @LogSql
    public Long insert(SqlParams sqlParams) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sqlParams.getSql(), Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < sqlParams.getArgs().size(); i++) {
                ps.setObject(i + 1, sqlParams.getArgs().get(i));
            }
            return ps;
        }, keyHolder);
        // Make sure the auto-increment ID of the database is Long type.
        return Cast.of(Objects.requireNonNull(keyHolder.getKey()).longValue());
    }

    /**
     * Update and return the number of affected rows.
     *
     * @param sqlParams SQL parameters
     */
    @LogSql
    public int update(SqlParams sqlParams) {
        return jdbcTemplate.update(sqlParams.getSql(), sqlParams.getArgsArray());
    }

    /**
     * Batch update and return the number of affected rows.
     *
     * @param sqlParams SQL parameters
     * @param batchArgs Batch parameters
     */
    @LogSql
    public int batchUpdate(SqlParams sqlParams, List<Object[]> batchArgs) {
        int[] affectedRows = jdbcTemplate.batchUpdate(sqlParams.getSql(), batchArgs);
        return Arrays.stream(affectedRows).sum();
    }

    /**
     * Query and return List<Map>.
     * MapRowMapper utility class is used to process row values, the key of Map is the camel fieldName.
     *
     * @param sqlParams SQL parameters
     * @return List<Map>
     */
    @LogSql
    public List<Map<String, Object>> queryForList(SqlParams sqlParams) {
        return jdbcTemplate.query(sqlParams.getSql(), new MapRowMapper(), sqlParams.getArgsArray());
    }

    /**
     * Query and return a single value of the specified type, such as count(*).
     *
     * @param sqlParams   SQL parameters
     * @param entityClass Entity class
     * @return Single value
     */
    @LogSql
    public Object queryForObject(SqlParams sqlParams, Class<?> entityClass) {
        return jdbcTemplate.queryForObject(sqlParams.getSql(), entityClass, sqlParams.getArgsArray());
    }

}