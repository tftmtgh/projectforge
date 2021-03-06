/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2020 Micromata GmbH, Germany (www.micromata.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.framework.persistence.database;

import de.micromata.genome.db.jpa.history.api.HistoryEntry;
import de.micromata.genome.db.jpa.tabattr.api.TimeableRow;
import de.micromata.genome.jpa.CriteriaUpdate;
import de.micromata.genome.jpa.metainf.EntityMetadata;
import de.micromata.genome.jpa.metainf.JpaMetadataEntityNotFoundException;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.property.RRule;
import org.apache.commons.lang3.StringUtils;
import org.projectforge.business.address.AddressDO;
import org.projectforge.business.address.AddressDao;
import org.projectforge.business.address.AddressbookDao;
import org.projectforge.business.fibu.*;
import org.projectforge.business.fibu.api.EmployeeService;
import org.projectforge.business.image.ImageService;
import org.projectforge.business.multitenancy.TenantDao;
import org.projectforge.business.multitenancy.TenantRegistryMap;
import org.projectforge.business.multitenancy.TenantService;
import org.projectforge.business.orga.*;
import org.projectforge.business.scripting.ScriptDO;
import org.projectforge.business.task.TaskDO;
import org.projectforge.business.teamcal.TeamCalConfig;
import org.projectforge.business.teamcal.event.model.TeamEventDO;
import org.projectforge.business.user.GroupDao;
import org.projectforge.business.user.ProjectForgeGroup;
import org.projectforge.business.user.UserXmlPreferencesDO;
import org.projectforge.business.vacation.model.VacationDO;
import org.projectforge.business.vacation.repository.VacationDao;
import org.projectforge.continuousdb.*;
import org.projectforge.framework.calendar.ICal4JUtils;
import org.projectforge.framework.configuration.Configuration;
import org.projectforge.framework.configuration.ConfigurationType;
import org.projectforge.framework.configuration.entities.ConfigurationDO;
import org.projectforge.framework.persistence.attr.impl.InternalAttrSchemaConstants;
import org.projectforge.framework.persistence.entities.AbstractBaseDO;
import org.projectforge.framework.persistence.history.HistoryBaseDaoAdapter;
import org.projectforge.framework.persistence.jpa.PfEmgrFactory;
import org.projectforge.framework.persistence.user.entities.GroupDO;
import org.projectforge.framework.persistence.user.entities.PFUserDO;
import org.projectforge.framework.persistence.user.entities.TenantDO;
import org.projectforge.framework.persistence.user.entities.UserRightDO;
import org.projectforge.framework.time.DateFormats;
import org.projectforge.framework.time.DateHelper;
import org.projectforge.framework.time.PFDateTime;
import org.projectforge.framework.time.PFDateTimeUtils;
import org.springframework.context.ApplicationContext;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.function.Predicate;

/**
 * @author Kai Reinhard (k.reinhard@micromata.de)
 * @deprecated Since version 6.18.0 please use flyway db migration.
 */
@Deprecated
public class DatabaseCoreUpdates
{
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DatabaseCoreUpdates.class);

  public static final String CORE_REGION_ID = "ProjectForge";

  private static final String VERSION_5_0 = "5.0";

  private static ApplicationContext applicationContext;
  private static String RESTART_RQUIRED = "no";

  static void setApplicationContext(final ApplicationContext applicationContext)
  {
    DatabaseCoreUpdates.applicationContext = applicationContext;
  }

  @SuppressWarnings("serial")
  public static List<UpdateEntry> getUpdateEntries()
  {
    final DatabaseService databaseService = applicationContext.getBean(DatabaseService.class);
    final PfEmgrFactory emf = applicationContext.getBean(PfEmgrFactory.class);
    final TenantDao tenantDao = applicationContext.getBean(TenantDao.class);

    final List<UpdateEntry> list = new ArrayList<>();

    ////////////////////////////////////////////////////////////////////
    // 6.17.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.17.0", "2017-08-16",
        "Add uid to addresses.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.16.0");
        if (!addressHasUid()) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }

        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!addressHasUid()) {
          databaseService.updateSchema();
          List<DatabaseResultRow> resultList = databaseService.query("select pk from t_address where uid is null");
          for (DatabaseResultRow row : resultList) {
            Integer id = (Integer) row.getEntry(0).getValue();
            UUID uid = UUID.randomUUID();
            String sql = "UPDATE t_address SET uid = '" + uid.toString() + "' WHERE pk = " + id;
            databaseService.execute(sql);
          }
        }
        return UpdateRunningStatus.DONE;
      }

      private boolean addressHasUid()
      {
        if (!databaseService.doesTableAttributeExist("t_address", "uid")) {
          return false;
        }

        if (databaseService.query("select * from t_address LIMIT 1").size() == 0) {
          return true;
        }

        return databaseService.query("select * from t_address where uid is not null LIMIT 1").size() > 0;
      }
    });

    ////////////////////////////////////////////////////////////////////
    // 6.16.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.16.0", "2017-08-01",
        "Remove unique constraints from EmployeeTimedAttrDO and EmployeeConfigurationTimedAttrDO. Add thumbnail for address images. Add addressbooks, remove tasks from addresses.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.16.0");
        if (oldUniqueConstraint() || isImageDataPreviewMissing() || !checkForAddresses()) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }

        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        // update unique constraint
        if (oldUniqueConstraint()) {
          String uniqueConstraint1 = databaseService.getUniqueConstraintName("t_fibu_employee_timedattr", "parent", "propertyName");
          String uniqueConstraint2 = databaseService.getUniqueConstraintName("T_PLUGIN_EMPLOYEE_CONFIGURATION_TIMEDATTR", "parent", "propertyName");

          if (uniqueConstraint1 != null) {
            databaseService.execute("ALTER TABLE t_fibu_employee_timedattr DROP CONSTRAINT " + uniqueConstraint1);
          }

          if (uniqueConstraint2 != null) {
            databaseService.execute("ALTER TABLE T_PLUGIN_EMPLOYEE_CONFIGURATION_TIMEDATTR DROP CONSTRAINT " + uniqueConstraint2);
          }
        }
        if (isImageDataPreviewMissing()) {
          final ImageService imageService = applicationContext.getBean(ImageService.class);
          databaseService.updateSchema();
          List<DatabaseResultRow> resultList = databaseService.query("select pk, imagedata from t_address where imagedata is not null");
          log.info("Found: " + resultList.size() + " event entries to update imagedata.");

          String sql = "UPDATE t_address SET image_data_preview = ?1 WHERE pk = ?2";
          PreparedStatement ps = null;
          try {
            ps = databaseService.getDataSource().getConnection().prepareStatement(sql);

            for (DatabaseResultRow row : resultList) {
              Integer id = (Integer) row.getEntry(0).getValue();
              byte[] imageDataPreview = imageService.resizeImage((byte[]) row.getEntry(1).getValue());
              try {
                ps.setInt(2, id);
                ps.setBytes(1, imageDataPreview);
                ps.executeUpdate();
              } catch (Exception e) {
                log.error(String.format("Error while updating event with id '%s' and new imageData. Ignoring it.", id, imageDataPreview));
              }
            }
            ps.close();
          } catch (SQLException e) {
            log.error("Error while updating imageDataPreview in Database : " + e.getMessage());
          }
        }

        //Add addressbook, remove task from addresses
        if (!checkForAddresses()) {
          databaseService.updateSchema();
          String taskUniqueConstraint = databaseService.getUniqueConstraintName("t_address", "task_id");
          if (!StringUtils.isBlank(taskUniqueConstraint)) {
            databaseService.execute("ALTER TABLE t_address DROP CONSTRAINT " + taskUniqueConstraint);
          }
          databaseService.execute("ALTER TABLE t_address DROP COLUMN task_id");
          databaseService.insertGlobalAddressbook();
          List<DatabaseResultRow> addressIds = databaseService.query("SELECT pk FROM t_address");
          addressIds.forEach(addressId -> {
            databaseService
                .execute("INSERT INTO t_addressbook_address (address_id, addressbook_id) VALUES (" + addressId.getEntry(0).getValue() + ", "
                    + AddressbookDao.GLOBAL_ADDRESSBOOK_ID + ")");
          });
          databaseService.execute("DELETE FROM t_configuration WHERE parameter = 'defaultTask4Addresses'");
        }

        return UpdateRunningStatus.DONE;
      }

      private boolean isImageDataPreviewMissing()
      {
        return !databaseService.doesTableAttributeExist("t_address", "image_data_preview") ||
            ((Long) databaseService.query("select count(*) from t_address where imagedata is not null AND imagedata != ''").get(0).getEntry(0).getValue())
                > 0L
                && databaseService.query("select pk from t_address where imagedata is not null AND image_data_preview is not null LIMIT 1").size() < 1;
      }

      private boolean oldUniqueConstraint()
      {
        return databaseService.doesUniqueConstraintExists("t_fibu_employee_timedattr", "parent", "propertyName")
            || databaseService.doesUniqueConstraintExists("T_PLUGIN_EMPLOYEE_CONFIGURATION_TIMEDATTR", "parent", "propertyName");
      }

      private boolean checkForAddresses()
      {
        return databaseService.doesTableExist("T_ADDRESSBOOK") && databaseService.query("select * from t_addressbook where pk = 1").size() > 0;
      }
    });

    ////////////////////////////////////////////////////////////////////
    // 6.15.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.15.0", "2017-07-19",
        "Add fields to event and event attendee table. Change unique constraint in event table. Refactoring invoice template.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.15.0");
        if (!hasRefactoredInvoiceFields()) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        if (this.missingFields() || oldUniqueConstraint() || noOwnership() || dtStampMissing()) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!hasRefactoredInvoiceFields()) {
          databaseService.updateSchema();
          migrateCustomerRef();
        }

        // update unique constraint
        if (oldUniqueConstraint()) {
          databaseService.execute("ALTER TABLE T_PLUGIN_CALENDAR_EVENT DROP CONSTRAINT unique_t_plugin_calendar_event_uid");
          databaseService.updateSchema();
        }

        // add missing fields
        if (missingFields()) {
          databaseService.updateSchema();
        }

        // check ownership
        if (noOwnership()) {
          List<DatabaseResultRow> resultList = databaseService.query("select e.pk, e.organizer from t_plugin_calendar_event e");
          log.info("Found: " + resultList.size() + " event entries to update ownership.");

          for (DatabaseResultRow row : resultList) {
            Integer id = (Integer) row.getEntry(0).getValue();
            String organizer = (String) row.getEntry(1).getValue();
            Boolean ownership = Boolean.TRUE;

            if (organizer != null && !organizer.equals("mailto:null")) {
              ownership = Boolean.FALSE;
            }

            try {
              databaseService.execute(String.format("UPDATE t_plugin_calendar_event SET ownership = '%s' WHERE pk = %s", ownership, id));
            } catch (Exception e) {
              log.error(String.format("Error while updating event with id '%s' and new ownership. Ignoring it.", id, ownership));
            }

            log.info(String.format("Updated event with id '%s' set ownership to '%s'", id, ownership));
          }
          log.info("Ownership computation DONE.");
        }

        // update DT_STAMP
        if (dtStampMissing()) {
          try {
            databaseService.execute("UPDATE t_plugin_calendar_event SET dt_stamp = last_update");
            log.info("Creating DT_STAMP values successful");
          } catch (Exception e) {
            log.error("Error while creating DT_STAMP values");
          }
        }

        return UpdateRunningStatus.DONE;
      }

      private boolean missingFields()
      {
        return !databaseService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT_ATTENDEE", "COMMON_NAME")
            || !databaseService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT_ATTENDEE", "CU_TYPE")
            || !databaseService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT_ATTENDEE", "RSVP")
            || !databaseService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT_ATTENDEE", "ROLE")
            || !databaseService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT_ATTENDEE", "ADDITIONAL_PARAMS")
            || !databaseService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT", "OWNERSHIP")
            || !databaseService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT", "ORGANIZER_ADDITIONAL_PARAMS")
            || !databaseService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT", "DT_STAMP");
      }

      private boolean oldUniqueConstraint()
      {
        return databaseService.doesUniqueConstraintExists("T_PLUGIN_CALENDAR_EVENT", "unique_t_plugin_calendar_event_uid");
      }

      private boolean noOwnership()
      {
        List<DatabaseResultRow> resultAll = databaseService.query("select pk from t_plugin_calendar_event LIMIT 1");

        if (resultAll.size() == 0) {
          return false;
        }

        List<DatabaseResultRow> result = databaseService.query("select pk from t_plugin_calendar_event where ownership is not null LIMIT 1");
        return result.size() == 0;
      }

      private boolean dtStampMissing()
      {
        List<DatabaseResultRow> resultAll = databaseService.query("select pk from t_plugin_calendar_event LIMIT 1");

        if (resultAll.size() == 0) {
          return false;
        }

        List<DatabaseResultRow> result = databaseService.query("select pk from t_plugin_calendar_event where dt_stamp is not null LIMIT 1");
        return result.size() == 0;
      }

      private void migrateCustomerRef()
      {
        //Migrate customer ref 1 & 2
        List<DatabaseResultRow> resultSet = databaseService.query("SELECT pk, customerref1, customerref2 FROM t_fibu_rechnung");

        for (DatabaseResultRow row : resultSet) {
          String pk = row.getEntry(0) != null && row.getEntry(0).getValue() != null ? row.getEntry(0).getValue().toString() : null;
          if (pk != null) {
            String cr1 = row.getEntry(1) != null && row.getEntry(1).getValue() != null ? row.getEntry(1).getValue().toString() : "";
            String cr2 = row.getEntry(2) != null && row.getEntry(2).getValue() != null ? row.getEntry(2).getValue().toString() : "";
            String newCr = "";
            if (!StringUtils.isEmpty(cr1) && !StringUtils.isEmpty(cr2)) {
              newCr = cr1 + "\r\n" + cr2;
            } else if (!StringUtils.isEmpty(cr1) && StringUtils.isEmpty(cr2)) {
              newCr = cr1;
            } else if (StringUtils.isEmpty(cr1) && !StringUtils.isEmpty(cr2)) {
              newCr = cr2;
            }
            databaseService.execute("UPDATE t_fibu_rechnung SET customerref1 = '" + newCr + "' WHERE pk = " + pk);
          }
        }
        databaseService.execute("ALTER TABLE t_fibu_rechnung DROP COLUMN customerref2");
      }

      private boolean hasRefactoredInvoiceFields()
      {
        return databaseService.doesTableAttributeExist("t_fibu_rechnung", "attachment")
            && !databaseService.doesTableAttributeExist("t_fibu_rechnung", "customerref2");
      }
    });

    ////////////////////////////////////////////////////////////////////
    // 6.13.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.13.0", "2017-06-21",
        "Correct error in until date of recurring events. Add fields to invoice DO.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.13.0");
        if (!hasNewInvoiceFields() || hasBadUntilDate()) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!hasNewInvoiceFields()) {
          databaseService.updateSchema();
        }

        if (hasBadUntilDate()) {
          Calendar calUntil = new GregorianCalendar(DateHelper.UTC);
          Calendar calStart = new GregorianCalendar(DateHelper.UTC);

          List<DatabaseResultRow> resultList = databaseService
              .query(
                  "select e.pk, e.start_date, e.recurrence_rule, e.recurrence_until, u.time_zone from t_plugin_calendar_event e, t_pf_user u where e.team_event_fk_creator = u.pk and e.recurrence_until is not null and all_day = false and to_char(recurrence_until, 'hh24:mi:ss') = '00:00:00'");
          log.info("Found: " + resultList.size() + " event entries to update until date.");

          for (DatabaseResultRow row : resultList) {
            Integer id = (Integer) row.getEntry(0).getValue();
            Date startDate = (Date) row.getEntry(1).getValue();
            String rruleStr = (String) row.getEntry(2).getValue();
            Date untilDate = (Date) row.getEntry(3).getValue();
            String timeZoneString = (String) row.getEntry(4).getValue();

            if (startDate == null || rruleStr == null || untilDate == null) {

              log.warn(String
                  .format("Processing event with id '%s', start date '%s', RRule '%s', and until date '%s' failed. Invalid data.",
                      id, startDate, rruleStr, untilDate));
              continue;
            }

            log.debug(String.format("Processing event with id '%s', start date '%s', RRule '%s', until date '%s', and timezone '%s'",
                id, startDate, rruleStr, untilDate, timeZoneString));

            TimeZone timeZone = TimeZone.getTimeZone(timeZoneString);
            if (timeZone == null) {
              timeZone = DateHelper.UTC;
            }

            calUntil.clear();
            calStart.clear();
            calUntil.setTimeZone(timeZone);
            calStart.setTimeZone(timeZone);

            // start processing
            calUntil.setTime(untilDate);
            calStart.setTime(startDate);

            // update date of start date to until date
            calStart.set(Calendar.YEAR, calUntil.get(Calendar.YEAR));
            calStart.set(Calendar.DAY_OF_YEAR, calUntil.get(Calendar.DAY_OF_YEAR));

            // add 23:59:59 to event start (next possible event time is +24h, 1 day)
            calStart.set(Calendar.HOUR_OF_DAY, 23);
            calStart.set(Calendar.MINUTE, 59);
            calStart.set(Calendar.SECOND, 59);
            calStart.set(Calendar.MILLISECOND, 0);

            // update recur until
            DateTime untilICal4J = new DateTime(calStart.getTime());
            untilICal4J.setUtc(true);
            RRule rRule = ICal4JUtils.calculateRRule(rruleStr);
            rRule.getRecur().setUntil(untilICal4J);

            try {
              databaseService
                  .execute(String.format("UPDATE t_plugin_calendar_event SET recurrence_rule = '%s', recurrence_until = '%s' WHERE pk = %s",
                      rRule.getValue(), DateHelper.formatIsoTimestamp(calStart.getTime()), id));
            } catch (Exception e) {
              log.error(String.format("Error while updating event with id '%s' and new recurrence_rule '%s', recurrence_until '%s'. Ignoring it.",
                  id, rRule.getValue(), DateHelper.formatIsoTimestamp(calStart.getTime())));
            }

            log.info(String.format("Updated event with id '%s' from '%s' to '%s'",
                id, DateHelper.formatIsoTimestamp(untilDate), DateHelper.formatIsoTimestamp(calStart.getTime())));
          }
          log.info("Until date migration is DONE.");
        }
        return UpdateRunningStatus.DONE;
      }

      private boolean hasNewInvoiceFields()
      {
        return databaseService.doesTableAttributeExist("t_fibu_rechnung", "customerref1") &&
            databaseService.doesTableAttributeExist("t_fibu_rechnung", "customeraddress");
      }

      private boolean hasBadUntilDate()
      {
        List<DatabaseResultRow> result = databaseService.query(
            "select pk from t_plugin_calendar_event where recurrence_until is not null and to_char(recurrence_until, 'hh24:mi:ss') = '00:00:00' and all_day = false LIMIT 1");
        return result.size() > 0;
      }
    });

    ////////////////////////////////////////////////////////////////////
    // 6.12.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.12.0", "2017-05-22",
        "Correct calendar exdates. Change address image data to AddressDO.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.12.0");
        if (hasISODates() || hasOldImageData()) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (hasOldImageData()) {
          databaseService.updateSchema();
          migrateImageData();
          deleteImageHistoryData();
          deleteImageAddressAttrData();
          log.info("Address image data migration DONE.");
        }

        if (hasISODates()) {
          SimpleDateFormat iCalFormatterWithTime = new SimpleDateFormat(DateFormats.ICAL_DATETIME_FORMAT);
          SimpleDateFormat iCalFormatterAllDay = new SimpleDateFormat(DateFormats.COMPACT_DATE);
          List<SimpleDateFormat> formatterPatterns = Arrays
              .asList(new SimpleDateFormat(DateFormats.ISO_TIMESTAMP_SECONDS), new SimpleDateFormat(DateFormats.ISO_TIMESTAMP_MINUTES),
                  new SimpleDateFormat(DateFormats.ISO_DATE), iCalFormatterWithTime, iCalFormatterAllDay);
          List<DatabaseResultRow> resultList = databaseService
              .query(
                  "SELECT pk, recurrence_ex_date, all_day FROM t_plugin_calendar_event te WHERE te.recurrence_ex_date IS NOT NULL AND te.recurrence_ex_date <> ''");
          log.info("Found: " + resultList.size() + " event entries to update.");
          for (DatabaseResultRow row : resultList) {
            Integer id = (Integer) row.getEntry(0).getValue();
            String exDateList = (String) row.getEntry(1).getValue();
            Boolean allDay = (Boolean) row.getEntry(2).getValue();
            log.debug("Event with id: " + id + " has exdate value: " + exDateList);
            String[] exDateArray = exDateList.split(",");
            List<String> finalExDates = new ArrayList<>();
            for (String exDateOld : exDateArray) {
              Date oldDate = null;
              for (SimpleDateFormat sdf : formatterPatterns) {
                try {
                  oldDate = sdf.parse(exDateOld);
                  break;
                } catch (ParseException e) {
                  if (log.isDebugEnabled()) {
                    log.debug("Date not parsable. Try another parser.");
                  }
                }
              }
              if (oldDate == null) {
                log.error("Date not parsable. Ignoring it: " + exDateOld);
                continue;
              }
              if (allDay != null && allDay) {
                finalExDates.add(iCalFormatterAllDay.format(oldDate));
              } else {
                finalExDates.add(iCalFormatterWithTime.format(oldDate));
              }
            }
            String newExDateValue = String.join(",", finalExDates);
            try {
              databaseService.execute("UPDATE t_plugin_calendar_event SET recurrence_ex_date = '" + newExDateValue + "' WHERE pk = " + id);
            } catch (Exception e) {
              log.error("Error while updating event with id: " + id + " and new exdatevalue: " + newExDateValue + " . Ignoring it.");
            }
          }
          log.info("Exdate migration DONE.");
        }
        return UpdateRunningStatus.DONE;
      }

      private boolean hasOldImageData()
      {
        return !databaseService.doesTableAttributeExist("T_ADDRESS", "imagedata")
            || databaseService.query("SELECT pk FROM t_address_attr WHERE propertyname = 'profileImageData' limit 1").size() > 0
            || databaseService.query("SELECT pk FROM t_pf_history_attr WHERE propertyname LIKE '%attrs.profileImageData%' limit 1").size() > 0;
      }

      private boolean hasISODates()
      {
        List<DatabaseResultRow> result = databaseService.query("SELECT * FROM T_PLUGIN_CALENDAR_EVENT WHERE recurrence_ex_date LIKE '%-%' LIMIT 1");
        return result.size() > 0;
      }

      private void deleteImageAddressAttrData()
      {
        List<DatabaseResultRow> attrResultList = databaseService.query("SELECT pk FROM t_address_attr WHERE propertyname = 'profileImageData'");
        for (DatabaseResultRow attrRow : attrResultList) {
          Integer attrId = (Integer) attrRow.getEntry(0).getValue();
          databaseService.execute("DELETE FROM t_address_attrdata WHERE parent_id = " + attrId);
          databaseService.execute("DELETE FROM t_address_attr WHERE pk = " + attrId);
        }
      }

      private void deleteImageHistoryData()
      {
        List<DatabaseResultRow> histAttrResultList = databaseService
            .query("SELECT pk FROM t_pf_history_attr WHERE propertyname LIKE '%attrs.profileImageData%'");
        for (DatabaseResultRow histAttrRow : histAttrResultList) {
          Long histAttrId = (Long) histAttrRow.getEntry(0).getValue();
          databaseService.execute("DELETE FROM t_pf_history_attr_data WHERE parent_pk = " + histAttrId);
          databaseService.execute("DELETE FROM t_pf_history_attr WHERE pk = " + histAttrId);
        }
      }

      private void migrateImageData()
      {
        AddressDao addressDao = applicationContext.getBean(AddressDao.class);
        List<AddressDO> allAddresses = addressDao.internalLoadAll();
        for (AddressDO ad : allAddresses) {
          byte[] imageData = ad.getAttribute("profileImageData", byte[].class);
          if (imageData != null && imageData.length > 0) {
            final PfEmgrFactory emf = applicationContext.getBean(PfEmgrFactory.class);
            emf.runInTrans(emgr -> {
              AddressDO addressDO = emgr.selectByPkAttached(AddressDO.class, ad.getId());
              addressDO.setImageData(imageData);
              emgr.update(addressDO);
              return null;
            });
          }
        }
      }
    });

    ////////////////////////////////////////////////////////////////////
    // 6.11.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.11.0", "2017-05-03",
        "Add discounts and konto informations. Add period of performance to invoices.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.11.0");
        if (isSchemaUpdateNecessary()) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (isSchemaUpdateNecessary()) {
          databaseService.updateSchema();
        }
        return UpdateRunningStatus.DONE;
      }

      private boolean isSchemaUpdateNecessary()
      {
        return !databaseService.doesTableAttributeExist("t_fibu_eingangsrechnung", "discountmaturity")
            || !databaseService.doesTableAttributeExist("t_fibu_rechnung", "discountmaturity")
            || !databaseService.doesTableAttributeExist("t_fibu_eingangsrechnung", "customernr")
            || !databaseService.doTableAttributesExist(RechnungDO.class, "periodOfPerformanceBegin", "periodOfPerformanceEnd")
            || !databaseService.doTableAttributesExist(RechnungsPositionDO.class, "periodOfPerformanceType", "periodOfPerformanceBegin",
            "periodOfPerformanceEnd");
      }
    });

    ////////////////////////////////////////////////////////////////////
    // 6.10.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.10.0", "2017-04-11",
        "Add column position_number to table T_FIBU_PAYMENT_SCHEDULE.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.10.0");
        if (!databaseService.doesTableAttributeExist("T_FIBU_PAYMENT_SCHEDULE", "position_number")) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!databaseService.doesTableAttributeExist("T_FIBU_PAYMENT_SCHEDULE", "position_number")) {
          databaseService.updateSchema();
        }
        return UpdateRunningStatus.DONE;
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.9.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.9.0", "2017-03-15",
        "Allow multiple substitutions on application for leave.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.9.0");
        if (!databaseService.doesTableExist("t_employee_vacation_substitution") ||
            databaseService.doesTableAttributeExist("t_employee_vacation", "substitution_id") ||
            uniqueConstraintMissing()) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }

        final Optional<Boolean> isColumnNullable = databaseService.isColumnNullable("T_PLUGIN_CALENDAR_EVENT", "UID");
        if (!isColumnNullable.isPresent() || isColumnNullable.get()) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }

        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!databaseService.doesTableExist("t_employee_vacation_substitution") || uniqueConstraintMissing()) {
          if (doesDuplicateUidsExists()) {
            handleDuplicateUids();
          }
          // Updating the schema
          databaseService.updateSchema();
        }

        final Optional<Boolean> isColumnNullable = databaseService.isColumnNullable("T_PLUGIN_CALENDAR_EVENT", "UID");
        if (!isColumnNullable.isPresent() || isColumnNullable.get()) {
          databaseService.execute("ALTER TABLE t_plugin_calendar_event ALTER COLUMN uid SET NOT NULL;");
        }

        if (databaseService.doesTableAttributeExist("t_employee_vacation", "substitution_id")) {
          migrateSubstitutions();
          // drop old substitution column
          databaseService.dropTableAttribute("t_employee_vacation", "substitution_id");
        }

        return UpdateRunningStatus.DONE;
      }

      private void handleDuplicateUids()
      {
        final PfEmgrFactory emf = applicationContext.getBean(PfEmgrFactory.class);
        emf.runInTrans(emgr -> {
          List<DatabaseResultRow> resultSet = databaseService
              .query("SELECT uid, COUNT(*) FROM t_plugin_calendar_event GROUP BY uid HAVING COUNT(*) > 1");
          for (DatabaseResultRow resultLine : resultSet) {
            List<TeamEventDO> teList = emgr
                .selectAttached(TeamEventDO.class, "SELECT t FROM TeamEventDO t WHERE t.uid = :uid", "uid", resultLine.getEntry(0).getValue());
            for (TeamEventDO te : teList) {
              te.setUid(TeamCalConfig.get().createEventUid());
              emgr.update(te);
            }
          }
          return null;
        });
      }

      private boolean doesDuplicateUidsExists()
      {
        List<DatabaseResultRow> resultSet = databaseService.query("SELECT uid, COUNT(*) FROM t_plugin_calendar_event GROUP BY uid HAVING COUNT(*) > 1");
        return resultSet != null && resultSet.size() > 0;
      }

      // migrate from old substitution column to new t_employee_vacation_substitution table
      private void migrateSubstitutions()
      {
        final VacationDao vacationDao = applicationContext.getBean(VacationDao.class);
        final EmployeeDao employeeDao = applicationContext.getBean(EmployeeDao.class);

        final List<DatabaseResultRow> resultRows = databaseService
            .query("SELECT pk, substitution_id FROM t_employee_vacation WHERE substitution_id IS NOT NULL;");

        for (final DatabaseResultRow row : resultRows) {
          final int vacationId = (int) row.getEntry("pk").getValue();
          final int substitutionId = (int) row.getEntry("substitution_id").getValue();
          final VacationDO vacation = vacationDao.internalGetById(vacationId);
          final EmployeeDO substitution = employeeDao.internalGetById(substitutionId);
          vacation.getSubstitutions().add(substitution);
          vacationDao.internalUpdate(vacation);
        }
      }

      private boolean uniqueConstraintMissing()
      {
        return !(databaseService.doesUniqueConstraintExists("T_PLUGIN_CALENDAR_EVENT", "unique_t_plugin_calendar_event_uid")
            || databaseService.doesUniqueConstraintExists("T_PLUGIN_CALENDAR_EVENT", "unique_t_plugin_calendar_event_uid_calendar_fk"));
      }
    });

    ////////////////////////////////////////////////////////////////////
    // 6.8.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.8.0", "2017-02-15",
        "Add calendar to vacation." + "Add possibility to create applications for leave of a half day.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.8.0");
        if (!databaseService.doesTableExist("t_employee_vacation_calendar")
            || !databaseService.doesTableAttributeExist("t_employee_vacation", "is_half_day")) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!databaseService.doesTableExist("t_employee_vacation_calendar")
            || !databaseService.doesTableAttributeExist("t_employee_vacation", "is_half_day")) {
          //Updating the schema
          databaseService.updateSchema();
        }
        return UpdateRunningStatus.DONE;
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.7.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.7.0", "2017-01-11",
        "Add payment type for order book position. Add users to project and order. Extend order position status.")
    {
      private static final String AUFTRAG_TABLE_COL_NAME = "status";
      private static final String AUFTRAG_OLD_STATUS_POTENZIAL = "GROB_KALKULATION";
      private final String AUFTRAG_NEW_STATUS_POTENZIAL = AuftragsStatus.POTENZIAL.name();

      private static final String AUFTRAG_POS_TABLE_COL_NAME = "status";
      private static final String AUFTRAG_POS_OLD_STATUS_BEAUFTRAGT = "BEAUFTRAGTE_OPTION";
      private final String AUFTRAG_POS_NEW_STATUS_BEAUFTRAGT = AuftragsPositionsStatus.BEAUFTRAGT.name();
      private static final String AUFTRAG_POS_OLD_STATUS_ABGELEHNT = "NICHT_BEAUFTRAGT";
      private final String AUFTRAG_POS_NEW_STATUS_ABGELEHNT = AuftragsPositionsStatus.ABGELEHNT.name();

      private boolean doesAuftragPotenzialNeedsUpdate()
      {
        return databaseService.doesTableRowExists(AuftragDO.class, AUFTRAG_TABLE_COL_NAME, AUFTRAG_OLD_STATUS_POTENZIAL, true);
      }

      private boolean doesAuftragPosBeauftragtNeedsUpdate()
      {
        return databaseService.doesTableRowExists(AuftragsPositionDO.class, AUFTRAG_POS_TABLE_COL_NAME, AUFTRAG_POS_OLD_STATUS_BEAUFTRAGT, true);
      }

      private boolean doesAuftragPosAbgelehntNeedsUpdate()
      {
        return databaseService.doesTableRowExists(AuftragsPositionDO.class, AUFTRAG_POS_TABLE_COL_NAME, AUFTRAG_POS_OLD_STATUS_ABGELEHNT, true);
      }

      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.7.0");
        if (this.isUpdateFibuAuftragPositionRequired()
            || !databaseService.doesTableAttributeExist("T_FIBU_PROJEKT", "projectmanager_fk")
            || !databaseService.doesTableAttributeExist("T_FIBU_PROJEKT", "headofbusinessmanager_fk")
            || !databaseService.doesTableAttributeExist("T_FIBU_PROJEKT", "salesmanager_fk")
            || !databaseService.doesTableAttributeExist("t_fibu_auftrag", "projectmanager_fk")
            || !databaseService.doesTableAttributeExist("t_fibu_auftrag", "headofbusinessmanager_fk")
            || !databaseService.doesTableAttributeExist("t_fibu_auftrag", "salesmanager_fk")) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }

        if (doesAuftragPotenzialNeedsUpdate() || doesAuftragPosBeauftragtNeedsUpdate() || doesAuftragPosAbgelehntNeedsUpdate()) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }

        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (this.isUpdateFibuAuftragPositionRequired()) {
          //Updating the schema
          databaseService.updateSchema();
          databaseService.execute("UPDATE t_fibu_auftrag_position SET paymentType = 'FESTPREISPAKET', art = NULL WHERE art = 'FESTPREISPAKET'");
          databaseService.execute("UPDATE t_fibu_auftrag_position SET paymentType = 'TIME_AND_MATERIALS', art = NULL WHERE art = 'TIME_AND_MATERIALS'");
          databaseService.execute("UPDATE t_fibu_auftrag_position SET art = 'WARTUNG' WHERE art = 'HOT_FIX'");
        }
        if (!databaseService.doesTableAttributeExist("T_FIBU_PROJEKT", "projectmanager_fk")
            || !databaseService.doesTableAttributeExist("T_FIBU_PROJEKT", "headofbusinessmanager_fk")
            || !databaseService.doesTableAttributeExist("T_FIBU_PROJEKT", "salesmanager_fk")
            || !databaseService.doesTableAttributeExist("t_fibu_auftrag", "projectmanager_fk")
            || !databaseService.doesTableAttributeExist("t_fibu_auftrag", "headofbusinessmanager_fk")
            || !databaseService.doesTableAttributeExist("t_fibu_auftrag", "salesmanager_fk")) {
          //Updating the schema
          databaseService.updateSchema();
        }

        if (doesAuftragPotenzialNeedsUpdate()) {
          databaseService.replaceTableCellStrings(AuftragDO.class, AUFTRAG_TABLE_COL_NAME, AUFTRAG_OLD_STATUS_POTENZIAL, AUFTRAG_NEW_STATUS_POTENZIAL);
        }
        if (doesAuftragPosBeauftragtNeedsUpdate()) {
          databaseService.replaceTableCellStrings(AuftragsPositionDO.class, AUFTRAG_POS_TABLE_COL_NAME, AUFTRAG_POS_OLD_STATUS_BEAUFTRAGT,
              AUFTRAG_POS_NEW_STATUS_BEAUFTRAGT);
        }
        if (doesAuftragPosAbgelehntNeedsUpdate()) {
          databaseService
              .replaceTableCellStrings(AuftragsPositionDO.class, AUFTRAG_POS_TABLE_COL_NAME, AUFTRAG_POS_OLD_STATUS_ABGELEHNT,
                  AUFTRAG_POS_NEW_STATUS_ABGELEHNT);
        }

        return UpdateRunningStatus.DONE;
      }

      private boolean isUpdateFibuAuftragPositionRequired()
      {
        // new field does not exist
        if (!databaseService.doesTableAttributeExist("t_fibu_auftrag_position", "paymentType"))
          return true;

        // old values in art field
        if (databaseService.doesTableRowExists(AuftragsPositionDO.class, "art", "FESTPREISPAKET", true) ||
            databaseService.doesTableRowExists(AuftragsPositionDO.class, "art", "TIME_AND_MATERIALS", true) ||
            databaseService.doesTableRowExists(AuftragsPositionDO.class, "art", "HOT_FIX", true))
          return true;

        return false;
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.6.1
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.6.1", "2016-12-23", "Add probability of occurrence to order book.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.6.1");
        if (!databaseService.doesTableAttributeExist("t_fibu_auftrag", "probability_of_occurrence")) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!databaseService.doesTableAttributeExist("t_fibu_auftrag", "probability_of_occurrence")) {
          //Updating the schema
          databaseService.updateSchema();
        }
        return UpdateRunningStatus.DONE;
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.6.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.6.0", "2016-12-14",
        "Add new visitorbook tables. Add table for vacation." +
            "Add new column in user table [lastWlanPasswordChange]. " +
            "Add new columns in order table [erfassungsDatum, entscheidungsDatum].")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.6.0");
        if (!databaseService.doesTableExist("T_EMPLOYEE_VACATION")
            || !databaseService.doesTableRowExists("T_CONFIGURATION", "PARAMETER", "hr.emailaddress",
            true)) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        } else if (
            !databaseService.doTablesExist(VisitorbookDO.class, VisitorbookTimedDO.class, VisitorbookTimedAttrDO.class, VisitorbookTimedAttrDataDO.class,
                VisitorbookTimedAttrWithDataDO.class) || !databaseService.doesGroupExists(ProjectForgeGroup.ORGA_TEAM)) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        } else if (!databaseService.doTableAttributesExist(PFUserDO.class, "lastWlanPasswordChange")
            || !databaseService.doTableAttributesExist(AuftragDO.class, "erfassungsDatum", "entscheidungsDatum")) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if ((!databaseService.doesTableExist("T_EMPLOYEE_VACATION")) || (!databaseService
            .doTablesExist(VisitorbookDO.class, VisitorbookTimedDO.class, VisitorbookTimedAttrDO.class, VisitorbookTimedAttrDataDO.class,
                VisitorbookTimedAttrWithDataDO.class))
            || !databaseService.doTableAttributesExist(PFUserDO.class, "lastWlanPasswordChange")
            || !databaseService.doTableAttributesExist(AuftragDO.class, "erfassungsDatum", "entscheidungsDatum")) {
          //Updating the schema
          databaseService.updateSchema();
        }
        if (!databaseService.doesTableRowExists("T_CONFIGURATION", "PARAMETER", "hr.emailaddress",
            true)) {
          final PfEmgrFactory emf = applicationContext.getBean(PfEmgrFactory.class);
          emf.runInTrans(emgr -> {
            ConfigurationDO confEntry = new ConfigurationDO();
            confEntry.setConfigurationType(ConfigurationType.STRING);
            confEntry.setGlobal(false);
            confEntry.setParameter("hr.emailaddress");
            confEntry.setStringValue("hr@management.de");
            emgr.insert(confEntry);
            return UpdateRunningStatus.DONE;
          });
        }
        if (!databaseService.doesGroupExists(ProjectForgeGroup.ORGA_TEAM)) {
          GroupDao groupDao = applicationContext.getBean(GroupDao.class);
          GroupDO orgaGroup = new GroupDO();
          orgaGroup.setName(ProjectForgeGroup.ORGA_TEAM.getName());
          groupDao.internalSave(orgaGroup);
        }

        return UpdateRunningStatus.DONE;
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.5.2
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.5.2", "2016-11-24",
        "Add creator to team event.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.5.2");
        if (!databaseService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT", "team_event_fk_creator")) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!databaseService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT", "team_event_fk_creator")) {
          //Updating the schema
          databaseService.updateSchema();
        }
        return UpdateRunningStatus.DONE;
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.4.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.4.0", "2016-10-12",
        "Move employee status to new timeable attribute.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.4.0");
        // ensure that the tenant exists, otherwise the following statements will fail with an SQL exception
        if (!databaseService.doTablesExist(TenantDO.class) || databaseService.internalIsTableEmpty("T_TENANT")) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }

        final EmployeeDao employeeDao = applicationContext.getBean(EmployeeDao.class);
        final boolean anyEmployeeWithAnOldStatusExists = databaseService.doTablesExist(EmployeeDO.class) &&
            employeeDao
                .internalLoadAll()
                .stream()
                .filter(e -> !e.isDeleted())
                .anyMatch(e -> e.getStatus() != null);

        final int employeeStatusGroupEntriesCount = databaseService
            .countTimeableAttrGroupEntries(EmployeeTimedDO.class, InternalAttrSchemaConstants.EMPLOYEE_STATUS_GROUP_NAME);

        if (anyEmployeeWithAnOldStatusExists && employeeStatusGroupEntriesCount <= 0) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        } else {
          return UpdatePreCheckStatus.ALREADY_UPDATED;
        }
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        migrateEmployeeStatusToAttr();

        return UpdateRunningStatus.DONE;
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.3.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.3.0", "2016-08-31",
        "Add column to attendee data table. Alter table column for ssh-key. Add HR group.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.3.0");
        if (!databaseService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT_ATTENDEE", "address_id")) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        } else if (databaseService.getDatabaseTableColumnLenght(PFUserDO.class, "ssh_public_key") < 4096) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        } else if (!databaseService.doesGroupExists(ProjectForgeGroup.HR_GROUP)) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        } else if (!databaseService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT", "uid")) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        } else {
          return UpdatePreCheckStatus.ALREADY_UPDATED;
        }
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!databaseService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT_ATTENDEE", "address_id")
            || !databaseService.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT", "uid")) {
          // fix unique constraint error
          uniqueConstraintWorkaround(databaseService, emf);

          // Updating the schema
          databaseService.updateSchema();
        }

        if (databaseService.getDatabaseTableColumnLenght(PFUserDO.class, "ssh_public_key") < 4096) {
          final Table userTable = new Table(PFUserDO.class);
          databaseService.alterTableColumnVarCharLength(userTable.getName(), "SSH_PUBLIC_KEY", 4096);
        }

        if (!databaseService.doesGroupExists(ProjectForgeGroup.HR_GROUP)) {
          emf.runInTrans(emgr -> {
            GroupDO hrGroup = new GroupDO();
            hrGroup.setName("PF_HR");
            hrGroup.setDescription("Users for having full access to the companies hr.");
            hrGroup.setCreated();
            hrGroup.setTenant(applicationContext.getBean(TenantService.class).getDefaultTenant());

            final Set<PFUserDO> usersToAddToHrGroup = new HashSet<>();

            final List<UserRightDO> employeeRights = emgr.selectAttached(UserRightDO.class,
                "SELECT r FROM UserRightDO r WHERE r.rightIdString = :rightId",
                "rightId",
                "FIBU_EMPLOYEE");
            employeeRights.forEach(sr -> {
              sr.setRightIdString("HR_EMPLOYEE");
              usersToAddToHrGroup.add(sr.getUser());
              emgr.update(sr);
            });

            final List<UserRightDO> salaryRights = emgr.selectAttached(UserRightDO.class,
                "SELECT r FROM UserRightDO r WHERE r.rightIdString = :rightId",
                "rightId",
                "FIBU_EMPLOYEE_SALARY");

            salaryRights.forEach(sr -> {
              sr.setRightIdString("HR_EMPLOYEE_SALARY");
              usersToAddToHrGroup.add(sr.getUser());
              emgr.update(sr);
            });

            usersToAddToHrGroup.forEach(hrGroup::addUser);

            emgr.insert(hrGroup);
            return hrGroup;
          });
        }

        return UpdateRunningStatus.DONE;
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.1.1
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.1.1", "2016-07-27",
        "Changed timezone of starttime of the configurable attributes. Add uid to attendee.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.1.1");
        if (!databaseService.doTablesExist(EmployeeTimedDO.class)) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }

        if (databaseService.isTableEmpty(EmployeeTimedDO.class)) {
          return UpdatePreCheckStatus.ALREADY_UPDATED;
        }

        final boolean timeFieldsOfAllEmployeeTimedDOsStartTimeAreZero = emf
            .runInTrans(emgr -> emgr.selectAllAttached(EmployeeTimedDO.class)
                .stream()
                .map(EmployeeTimedDO::getStartTime)
                .map(this::convertDateToLocalDateTimeInUTC)
                .map(localDateTime -> localDateTime.get(ChronoField.SECOND_OF_DAY))
                .allMatch(seconds -> seconds == 0));

        return timeFieldsOfAllEmployeeTimedDOsStartTimeAreZero ? UpdatePreCheckStatus.ALREADY_UPDATED : UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      private LocalDateTime convertDateToLocalDateTimeInUTC(final Date date) {
        final Instant instant = date.toInstant();
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
      }


      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!databaseService.doTablesExist(EmployeeTimedDO.class)) {
          // fix unique constraint error
          uniqueConstraintWorkaround(databaseService, emf);
          // Updating the schema
          databaseService.updateSchema();
        }

        return emf.runInTrans(emgr -> {
          emgr.selectAllAttached(EmployeeTimedDO.class)
              .forEach(this::normalizeStartTime);

          return UpdateRunningStatus.DONE;
        });
      }

      private void normalizeStartTime(final TimeableRow entity)
      {
        final Date oldStartTime = entity.getStartTime();
        final Date newStartTime = PFDateTimeUtils.getUTCBeginOfDay(oldStartTime);
        entity.setStartTime(newStartTime);
      }

    });

    ////////////////////////////////////////////////////////////////////
    // 6.1.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.1.0", "2016-07-14",
        "Adds several columns to employee table.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.1.0");
        if (!databaseService.doTableAttributesExist(EmployeeDO.class, "staffNumber")) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        // fix unique constraint error
        uniqueConstraintWorkaround(databaseService, emf);
        // Updating the schema
        databaseService.updateSchema();

        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 6.0.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "6.0.0", "2016-04-01",
        "Adds tenant table, tenant_id to all entities for multi-tenancy. Adds new history tables. Adds attr table for address. Adds t_configuration.is_global, t_pf_user.super_admin.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        log.info("Running pre-check for ProjectForge version 6.0.0");
        if (!databaseService.doTablesExist(TenantDO.class)
            || databaseService.internalIsTableEmpty("t_tenant") ||
            !databaseService.doTableAttributesExist(ConfigurationDO.class, "global") ||
            !databaseService.doTableAttributesExist(PFUserDO.class, "superAdmin")) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }

        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @SuppressWarnings({ "unchecked", "rawtypes" })
      @Override
      public UpdateRunningStatus runUpdate()
      {
        // fix unique constraint error
        uniqueConstraintWorkaround(databaseService, emf);

        // drop foreign keys for all known tables
        // ------------------------------------------------------------------------------------------------------------------------
        int count = databaseService.dropForeignKeys();
        log.info(String.format("%s foreign keys are dropped due to the new hibernate naming schema", count));
        // ------------------------------------------------------------------------------------------------------------------------

        // Updating the schema
        databaseService.updateSchema();

        // init default tenant
        TenantDO defaultTenant;
        if (databaseService.internalIsTableEmpty("t_tenant")) {
          try {
            defaultTenant = databaseService.insertDefaultTenant();
          } catch (Exception e) {
            e.printStackTrace();
            return UpdateRunningStatus.FAILED;
          }
        } else {
          defaultTenant = tenantDao.getDefaultTenant();
        }

        //Insert default tenant on every entity
        log.info("Start adding default tenant to entities.");
        List<EntityMetadata> entities = emf.getMetadataRepository().getTableEntities();
        Collections.reverse(entities);
        for (EntityMetadata entityClass : entities) {
          if (AbstractBaseDO.class.isAssignableFrom(entityClass.getJavaType())) {
            try {
              log.info("Set tenant id for entities of type: " + entityClass.getJavaType());
              emf.tx().go(emgr -> {
                Class<? extends AbstractBaseDO> entity = (Class<? extends AbstractBaseDO>) entityClass.getJavaType();
                CriteriaUpdate<? extends AbstractBaseDO> cu = CriteriaUpdate.createUpdate(entity);
                cu.set("tenant", defaultTenant);
                emgr.update(cu);
                return null;
              });
            } catch (Exception e) {
              log.error("Failed to update default tenant for entities of type: " + entityClass.getJavaType());
            }
          }
          if (UserXmlPreferencesDO.class.isAssignableFrom(entityClass.getJavaType())) {
            try {
              emf.runInTrans(emgr -> {
                log.info("Set tenant id for entities of type: " + UserXmlPreferencesDO.class);
                CriteriaUpdate<UserXmlPreferencesDO> cu = CriteriaUpdate.createUpdate(UserXmlPreferencesDO.class);
                cu.set("tenant", defaultTenant);
                emgr.update(cu);
                return null;
              });
            } catch (Exception e) {
              log.error("Failed to update default tenant for user xml prefs.");
            }
          }
        }
        log.info("Finished adding default tenant to entities.");

        // assign default tenant to each user
        log.info("Start assigning users to default tenant.");
        try {
          emf.tx().go(emgr -> {
            TenantDO attachedDefaultTenant = emgr.selectByPkAttached(TenantDO.class, defaultTenant.getId());
            List<PFUserDO> users = emgr.selectAttached(PFUserDO.class, "select u from PFUserDO u");
            for (PFUserDO user : users) {
              log.info("Assign user with id: " + user.getId() + " to default tenant.");
              attachedDefaultTenant.getAssignedUsers().add(user);
              emgr.update(attachedDefaultTenant);
            }
            return null;
          });
        } catch (Exception e) {
          log.error("Failed to assign users to default tenant.");
        }
        log.info("Finished assigning users to default tenant.");

        //History migration
        log.info("Start migrating history data.");
        HistoryMigrateService ms = applicationContext.getBean(HistoryMigrateService.class);
        long start = System.currentTimeMillis();
        try {
          ms.migrate();
        } catch (Exception ex) {
          log.error("Error while migrating history data", ex);
        }
        log.info("History Migration took: " + (System.currentTimeMillis() - start) / 1000 + " sec");
        log.info("Finished migrating history data.");

        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // /////////////////////////////////////////////////////////////////
    // 5.5
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(
        CORE_REGION_ID,
        "5.5",
        "2014-08-11",
        "Adds t_group.ldap_values, t_fibu_auftrag_position.period_of_performance_type, t_fibu_auftrag_position.mode_of_payment_type, t_fibu_payment_schedule, t_fibu_auftrag.period_of_performance_{begin|end}, length of t_address.public_key increased.")
    {

      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        if (RESTART_RQUIRED.equals("v5.5")) {
          return UpdatePreCheckStatus.RESTART_REQUIRED;
        }
        log.info("Running pre-check for ProjectForge version 5.5");
        if (!databaseService.doTableAttributesExist(EmployeeDO.class, "weeklyWorkingHours")) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        if (!databaseService.doTableAttributesExist(GroupDO.class, "ldapValues")) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        if (!databaseService.doTableAttributesExist(AuftragsPositionDO.class, "periodOfPerformanceType",
            "modeOfPaymentType")) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        if (!databaseService.doTableAttributesExist(AuftragDO.class, "periodOfPerformanceBegin",
            "periodOfPerformanceEnd")) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        if (!databaseService.doTablesExist(PaymentScheduleDO.class)) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!databaseService.doTableAttributesExist(EmployeeDO.class, "weeklyWorkingHours")) {
          // No length check available so assume enlargement if ldapValues doesn't yet exist:
          final Table addressTable = new Table(AddressDO.class);
          databaseService.alterTableColumnVarCharLength(addressTable.getName(), "public_key", 20000);

          // TODO HIBERNATE5 no longer supported
          //          final Table propertyDeltaTable = new Table(PropertyDelta.class);
          //          dao.alterTableColumnVarCharLength(propertyDeltaTable.getName(), "old_value", 20000);
          //          dao.alterTableColumnVarCharLength(propertyDeltaTable.getName(), "new_value", 20000);

          final Table employeeTable = new Table(EmployeeDO.class);
          databaseService.renameTableAttribute(employeeTable.getName(), "wochenstunden", "old_weekly_working_hours");
          databaseService.addTableAttributes(EmployeeDO.class, "weeklyWorkingHours");
          final List<DatabaseResultRow> rows = databaseService
              .query("select pk, old_weekly_working_hours from t_fibu_employee");
          if (rows != null) {
            for (final DatabaseResultRow row : rows) {
              final Integer pk = (Integer) row.getEntry("pk").getValue();
              final Integer oldWeeklyWorkingHours = (Integer) row.getEntry("old_weekly_working_hours").getValue();
              if (oldWeeklyWorkingHours == null) {
                continue;
              }
              databaseService.update("update t_fibu_employee set weekly_working_hours=? where pk=?",
                  new BigDecimal(oldWeeklyWorkingHours), pk);
            }
          }
        }
        if (!databaseService.doTableAttributesExist(GroupDO.class, "ldapValues")) {
          databaseService.addTableAttributes(GroupDO.class, "ldapValues");
        }
        if (!databaseService.doTableAttributesExist(AuftragsPositionDO.class, "periodOfPerformanceType",
            "modeOfPaymentType")) {
          databaseService.addTableAttributes(AuftragsPositionDO.class, "modeOfPaymentType");
          databaseService.addTableAttributes(AuftragsPositionDO.class, "periodOfPerformanceType");
        }
        if (!databaseService.doTableAttributesExist(AuftragDO.class, "periodOfPerformanceBegin",
            "periodOfPerformanceEnd")) {
          databaseService.addTableAttributes(AuftragDO.class, "periodOfPerformanceBegin");
          databaseService.addTableAttributes(AuftragDO.class, "periodOfPerformanceEnd");
        }
        if (!databaseService.doTablesExist(PaymentScheduleDO.class)) {
          new SchemaGenerator(databaseService).add(TenantDO.class).createSchema();
          new SchemaGenerator(databaseService).add(PaymentScheduleDO.class).createSchema();
          databaseService.createMissingIndices();
        }

        RESTART_RQUIRED = "v5.5";
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 5.3
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "5.3", "2013-11-24",
        "Adds t_pf_user.last_password_change, t_pf_user.password_salt.")
    {

      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        if (!databaseService.doTableAttributesExist(PFUserDO.class, "lastPasswordChange", "passwordSalt")) {
          return UpdatePreCheckStatus.READY_FOR_UPDATE;
        }
        return UpdatePreCheckStatus.ALREADY_UPDATED;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!databaseService.doTableAttributesExist(PFUserDO.class, "lastPasswordChange", "passwordSalt")) {
          databaseService.addTableAttributes(PFUserDO.class, "lastPasswordChange", "passwordSalt");
        }
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 5.2
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(
        CORE_REGION_ID,
        "5.2",
        "2013-05-13",
        "Adds t_fibu_auftrag_position.time_of_performance_{start|end}, t_script.file{_name} and changes type of t_script.script{_backup} to byte[].")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        if (databaseService.doTableAttributesExist(ScriptDO.class, "file", "filename")
            && databaseService.doTableAttributesExist(AuftragsPositionDO.class, "periodOfPerformanceBegin", "periodOfPerformanceEnd")) {
          return UpdatePreCheckStatus.ALREADY_UPDATED;
        }
        return UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!databaseService.doTableAttributesExist(ScriptDO.class, "file", "filename")) {
          databaseService.addTableAttributes(ScriptDO.class, "file", "filename");
          final Table scriptTable = new Table(ScriptDO.class);
          databaseService.renameTableAttribute(scriptTable.getName(), "script", "old_script");
          databaseService.renameTableAttribute(scriptTable.getName(), "scriptbackup", "old_script_backup");
          databaseService.addTableAttributes(ScriptDO.class, "script", "scriptBackup");
          final List<DatabaseResultRow> rows = databaseService
              .query("select pk, old_script, old_script_backup from t_script");
          if (rows != null) {
            for (final DatabaseResultRow row : rows) {
              final Integer pk = (Integer) row.getEntry("pk").getValue();
              final String oldScript = (String) row.getEntry("old_script").getValue();
              final String oldScriptBackup = (String) row.getEntry("old_script_backup").getValue();
              final ScriptDO script = new ScriptDO();
              script.setScriptAsString(oldScript);
              script.setScriptBackupAsString(oldScriptBackup);
              databaseService.update("update t_script set script=?, script_backup=? where pk=?", script.getScript(),
                  script.getScriptBackup(), pk);
            }
          }
        }
        if (!databaseService.doTableAttributesExist(AuftragsPositionDO.class, "periodOfPerformanceBegin",
            "periodOfPerformanceEnd")) {
          databaseService.addTableAttributes(AuftragsPositionDO.class, "periodOfPerformanceBegin",
              "periodOfPerformanceEnd");
        }
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 5.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, VERSION_5_0, "2013-02-15",
        "Adds t_fibu_rechnung.konto, t_pf_user.ssh_public_key, fixes contract.IN_PROGRES -> contract.IN_PROGRESS")
    {
      final Table rechnungTable = new Table(RechnungDO.class);

      final Table userTable = new Table(PFUserDO.class);

      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        int entriesToMigrate = 0;
        if (!databaseService.isVersionUpdated(CORE_REGION_ID, VERSION_5_0)) {
          entriesToMigrate = databaseService.queryForInt("select count(*) from t_contract where status='IN_PROGRES'");
        }
        return (databaseService.doTableAttributesExist(rechnungTable, "konto")
            && databaseService.doTableAttributesExist(userTable, "sshPublicKey")
            && entriesToMigrate == 0)
            ? UpdatePreCheckStatus.ALREADY_UPDATED : UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!databaseService.doTableAttributesExist(rechnungTable, "konto")) {
          databaseService.addTableAttributes(rechnungTable, new TableAttribute(RechnungDO.class, "konto"));
        }
        if (!databaseService.doTableAttributesExist(userTable, "sshPublicKey")) {
          databaseService.addTableAttributes(userTable, new TableAttribute(PFUserDO.class, "sshPublicKey"));
        }
        final int entriesToMigrate = databaseService
            .queryForInt("select count(*) from t_contract where status='IN_PROGRES'");
        if (entriesToMigrate > 0) {
          databaseService.execute("update t_contract set status='IN_PROGRESS' where status='IN_PROGRES'", true);
        }
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 4.3.1
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "4.3.1", "2013-01-29", "Adds t_fibu_projekt.konto")
    {
      final Table projektTable = new Table(ProjektDO.class);

      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        return databaseService.doTableAttributesExist(projektTable, "konto") //
            ? UpdatePreCheckStatus.ALREADY_UPDATED
            : UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!databaseService.doTableAttributesExist(projektTable, "konto")) {
          databaseService.addTableAttributes(projektTable, new TableAttribute(ProjektDO.class, "konto"));
        }
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 4.2
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(
        CORE_REGION_ID,
        "4.2",
        "2012-08-09",
        "Adds t_pf_user.authenticationToken|local_user|restricted_user|deactivated|ldap_values, t_group.local_group, t_fibu_rechnung|eingangsrechnung|auftrag(=incoming and outgoing invoice|order).ui_status_as_xml")
    {
      final Table userTable = new Table(PFUserDO.class);

      final Table groupTable = new Table(GroupDO.class);

      final Table outgoingInvoiceTable = new Table(RechnungDO.class);

      final Table incomingInvoiceTable = new Table(EingangsrechnungDO.class);

      final Table orderTable = new Table(AuftragDO.class);

      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        return databaseService.doTableAttributesExist(userTable, "authenticationToken", "localUser", "restrictedUser",
            "deactivated", "ldapValues") //
            && databaseService.doTableAttributesExist(groupTable, "localGroup")
            // , "nestedGroupsAllowed", "nestedGroupIds") == true //
            && databaseService.doTableAttributesExist(outgoingInvoiceTable, "uiStatusAsXml") //
            && databaseService.doTableAttributesExist(incomingInvoiceTable, "uiStatusAsXml") //
            && databaseService.doTableAttributesExist(orderTable, "uiStatusAsXml") //
            ? UpdatePreCheckStatus.ALREADY_UPDATED : UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!databaseService.doTableAttributesExist(userTable, "authenticationToken")) {
          databaseService.addTableAttributes(userTable, new TableAttribute(PFUserDO.class, "authenticationToken"));
        }
        if (!databaseService.doTableAttributesExist(userTable, "localUser")) {
          databaseService.addTableAttributes(userTable,
              new TableAttribute(PFUserDO.class, "localUser").setDefaultValue("false"));
        }
        if (!databaseService.doTableAttributesExist(userTable, "restrictedUser")) {
          databaseService.addTableAttributes(userTable,
              new TableAttribute(PFUserDO.class, "restrictedUser").setDefaultValue("false"));
        }
        if (!databaseService.doTableAttributesExist(userTable, "deactivated")) {
          databaseService.addTableAttributes(userTable,
              new TableAttribute(PFUserDO.class, "deactivated").setDefaultValue("false"));
        }
        if (!databaseService.doTableAttributesExist(userTable, "ldapValues")) {
          databaseService.addTableAttributes(userTable, new TableAttribute(PFUserDO.class, "ldapValues"));
        }
        if (!databaseService.doTableAttributesExist(groupTable, "localGroup")) {
          databaseService.addTableAttributes(groupTable,
              new TableAttribute(GroupDO.class, "localGroup").setDefaultValue("false"));
        }
        // if (dao.doesTableAttributesExist(groupTable, "nestedGroupsAllowed") == false) {
        // dao.addTableAttributes(groupTable, new TableAttribute(GroupDO.class, "nestedGroupsAllowed").setDefaultValue("true"));
        // }
        // if (dao.doesTableAttributesExist(groupTable, "nestedGroupIds") == false) {
        // dao.addTableAttributes(groupTable, new TableAttribute(GroupDO.class, "nestedGroupIds"));
        // }
        if (!databaseService.doTableAttributesExist(outgoingInvoiceTable, "uiStatusAsXml")) {
          databaseService.addTableAttributes(outgoingInvoiceTable,
              new TableAttribute(RechnungDO.class, "uiStatusAsXml"));
        }
        if (!databaseService.doTableAttributesExist(incomingInvoiceTable, "uiStatusAsXml")) {
          databaseService.addTableAttributes(incomingInvoiceTable,
              new TableAttribute(EingangsrechnungDO.class, "uiStatusAsXml"));
        }
        if (!databaseService.doTableAttributesExist(orderTable, "uiStatusAsXml")) {
          databaseService.addTableAttributes(orderTable, new TableAttribute(AuftragDO.class, "uiStatusAsXml"));
        }
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 4.1
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "4.1", "2012-04-21",
        "Adds t_pf_user.first_day_of_week and t_pf_user.hr_planning.")
    {
      final Table userTable = new Table(PFUserDO.class);

      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        return databaseService.doTableAttributesExist(userTable, "firstDayOfWeek", "hrPlanning") //
            ? UpdatePreCheckStatus.ALREADY_UPDATED
            : UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!databaseService.doTableAttributesExist(userTable, "firstDayOfWeek")) {
          databaseService.addTableAttributes(userTable, new TableAttribute(PFUserDO.class, "firstDayOfWeek"));
        }
        if (!databaseService.doTableAttributesExist(userTable, "hrPlanning")) {
          databaseService.addTableAttributes(userTable,
              new TableAttribute(PFUserDO.class, "hrPlanning").setDefaultValue("true"));
        }
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 4.0
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "4.0", "2012-04-18",
        "Adds 6th parameter to t_script and payment_type to t_fibu_eingangsrechnung.")
    {
      final Table scriptTable = new Table(ScriptDO.class);

      final Table eingangsrechnungTable = new Table(EingangsrechnungDO.class);

      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        return databaseService.doTableAttributesExist(scriptTable, "parameter6Name", "parameter6Type") //
            && databaseService.doTableAttributesExist(eingangsrechnungTable, "paymentType") //
            ? UpdatePreCheckStatus.ALREADY_UPDATED : UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        if (!databaseService.doTableAttributesExist(scriptTable, "parameter6Name")) {
          databaseService.addTableAttributes(scriptTable, new TableAttribute(ScriptDO.class, "parameter6Name"));
        }
        if (!databaseService.doTableAttributesExist(scriptTable, "parameter6Type")) {
          databaseService.addTableAttributes(scriptTable, new TableAttribute(ScriptDO.class, "parameter6Type"));
        }
        if (!databaseService.doTableAttributesExist(eingangsrechnungTable, "paymentType")) {
          databaseService.addTableAttributes(eingangsrechnungTable,
              new TableAttribute(EingangsrechnungDO.class, "paymentType"));
        }
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 3.6.2
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(
        CORE_REGION_ID,
        "3.6.1.3",
        "2011-12-05",
        "Adds columns t_kunde.konto_id, t_fibu_eingangsrechnung.konto_id, t_konto.status, t_task.protection_of_privacy and t_address.communication_language.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        final Table kundeTable = new Table(KundeDO.class);
        final Table eingangsrechnungTable = new Table(EingangsrechnungDO.class);
        final Table kontoTable = new Table(KontoDO.class);
        final Table taskTable = new Table(TaskDO.class);
        final Table addressTable = new Table(AddressDO.class);
        return databaseService.doTableAttributesExist(kundeTable, "konto") //
            && databaseService.doTableAttributesExist(eingangsrechnungTable, "konto") //
            && databaseService.doTableAttributesExist(kontoTable, "status") //
            && databaseService.doTableAttributesExist(addressTable, "communicationLanguage") //
            && databaseService.doTableAttributesExist(taskTable, "protectionOfPrivacy") //
            ? UpdatePreCheckStatus.ALREADY_UPDATED : UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        final Table kundeTable = new Table(KundeDO.class);
        if (!databaseService.doTableAttributesExist(kundeTable, "konto")) {
          databaseService.addTableAttributes(kundeTable, new TableAttribute(KundeDO.class, "konto"));
        }
        final Table eingangsrechnungTable = new Table(EingangsrechnungDO.class);
        if (!databaseService.doTableAttributesExist(eingangsrechnungTable, "konto")) {
          databaseService.addTableAttributes(eingangsrechnungTable,
              new TableAttribute(EingangsrechnungDO.class, "konto"));
        }
        final Table kontoTable = new Table(KontoDO.class);
        if (!databaseService.doTableAttributesExist(kontoTable, "status")) {
          databaseService.addTableAttributes(kontoTable, new TableAttribute(KontoDO.class, "status"));
        }
        final Table taskTable = new Table(TaskDO.class);
        if (!databaseService.doTableAttributesExist(taskTable, "protectionOfPrivacy")) {
          databaseService.addTableAttributes(taskTable,
              new TableAttribute(TaskDO.class, "protectionOfPrivacy").setDefaultValue("false"));
        }
        final Table addressTable = new Table(AddressDO.class);
        if (!databaseService.doTableAttributesExist(addressTable, "communicationLanguage")) {
          databaseService.addTableAttributes(addressTable,
              new TableAttribute(AddressDO.class, "communicationLanguage"));
        }
        databaseService.createMissingIndices();
        return UpdateRunningStatus.DONE;
      }
    });

    // /////////////////////////////////////////////////////////////////
    // 3.5.4
    // /////////////////////////////////////////////////////////////////
    list.add(new UpdateEntryImpl(CORE_REGION_ID, "3.5.4", "2011-02-24",
        "Adds table t_database_update. Adds attribute (excel_)date_format, hour_format_24 to table t_pf_user.")
    {
      @Override
      public UpdatePreCheckStatus runPreCheck()
      {
        final Table dbUpdateTable = new Table(DatabaseUpdateDO.class);
        final Table userTable = new Table(PFUserDO.class);
        return databaseService.doExist(dbUpdateTable)
            && databaseService.doTableAttributesExist(userTable, "dateFormat", "excelDateFormat",
            "timeNotation") //
            ? UpdatePreCheckStatus.ALREADY_UPDATED : UpdatePreCheckStatus.READY_FOR_UPDATE;
      }

      @Override
      public UpdateRunningStatus runUpdate()
      {
        final Table dbUpdateTable = new Table(DatabaseUpdateDO.class);
        final Table userTable = new Table(PFUserDO.class);
        dbUpdateTable.addAttributes("updateDate", "regionId", "versionString", "executionResult", "executedBy",
            "description");
        databaseService.createTable(dbUpdateTable);
        databaseService.addTableAttributes(userTable, new TableAttribute(PFUserDO.class, "dateFormat"));
        databaseService.addTableAttributes(userTable, new TableAttribute(PFUserDO.class, "excelDateFormat"));
        databaseService.addTableAttributes(userTable, new TableAttribute(PFUserDO.class, "timeNotation"));
        databaseService.createMissingIndices();
        TenantRegistryMap.getInstance().setAllUserGroupCachesAsExpired();
        //TODO: Lösung finden!!!
        //Registry.instance().getUserCache().setExpired();
        return UpdateRunningStatus.DONE;
      }
    });
    return list;
  }

  private static void uniqueConstraintWorkaround(final DatabaseService dus, final PfEmgrFactory emf)
  {
    EntityMetadata pce;

    try {
      pce = emf.getMetadataRepository().getEntityMetaDataBySimpleClassName("TeamEventDO");
    } catch (JpaMetadataEntityNotFoundException e) {
      log.error("No JPA class found for TeamEventDO");
      pce = null;
    }

    if (!dus.doesTableAttributeExist("T_PLUGIN_CALENDAR_EVENT", "uid") && pce != null) {
      // required workaround, because null values are not accepted
      final String type = dus.getAttribute(pce.getJavaType(), "uid");
      final String command1 = String.format("ALTER TABLE T_PLUGIN_CALENDAR_EVENT ADD COLUMN UID %s DEFAULT 'default value'", type);

      dus.execute(command1);
      dus.execute("ALTER TABLE T_PLUGIN_CALENDAR_EVENT ALTER COLUMN UID SET NOT NULL");
      dus.execute("ALTER TABLE T_PLUGIN_CALENDAR_EVENT ALTER COLUMN UID DROP DEFAULT");
    }
  }

  public static void migrateEmployeeStatusToAttr()
  {
    final EmployeeService employeeService = applicationContext.getBean(EmployeeService.class);
    final EmployeeDao employeeDao = applicationContext.getBean(EmployeeDao.class);

    final List<EmployeeDO> employees = employeeDao.internalLoadAll();
    employees.forEach(employee -> {
      final EmployeeStatus status = employee.getStatus();
      if (status != null) {
        final EmployeeTimedDO newAttrRow = employeeService.addNewTimeAttributeRow(employee, InternalAttrSchemaConstants.EMPLOYEE_STATUS_GROUP_NAME);
        newAttrRow.setStartTime(getDateForStatus(employee));
        newAttrRow.putAttribute(InternalAttrSchemaConstants.EMPLOYEE_STATUS_DESC_NAME, status.getI18nKey());
        employeeDao.internalUpdate(employee);
      }
    });
  }

  private static Date getDateForStatus(final EmployeeDO employee)
  {
    // At first try to find the last change of the employee status in the history ...
    final Optional<Date> lastChange = findLastChangeOfEmployeeStatusInHistory(employee);
    if (lastChange.isPresent()) {
      // convert date from UTC to current zone date
      final TimeZone utc = TimeZone.getTimeZone("UTC");
      final TimeZone currentTimeZone = Configuration.getInstance().getDefaultTimeZone();
      final Date dateInCurrentTimezone = convertDateIntoOtherTimezone(lastChange.get(), utc, currentTimeZone);
      return PFDateTimeUtils.getUTCBeginOfDay(dateInCurrentTimezone);
    }

    // ... if there is nothing in the history, then use the entrittsdatum ...
    final Date eintrittsDatum = employee.getEintrittsDatum();
    if (eintrittsDatum != null) {
      return PFDateTime.from(eintrittsDatum, false, PFDateTimeUtils.TIMEZONE_UTC).getBeginOfDay().getUtilDate();
    }

    // ... if there is no eintrittsdatum, use the current date.
    return PFDateTime.now().getBeginOfDay().getUtilDate();
  }

  private static Date convertDateIntoOtherTimezone(final Date date, final TimeZone from, final TimeZone to) {
    final Instant instant = date.toInstant();
    final LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, to.toZoneId());
    final Instant instant2 = localDateTime.toInstant(from.toZoneId().getRules().getOffset(instant));
    return Date.from(instant2);
  }

  private static Optional<Date> findLastChangeOfEmployeeStatusInHistory(final EmployeeDO employee)
  {
    final Predicate<HistoryEntry> hasStatusChangeHistoryEntries = historyEntry ->
        ((HistoryEntry<?>) historyEntry)
            .getDiffEntries()
            .stream()
            .anyMatch(
                diffEntry -> diffEntry.getPropertyName().startsWith("status")
            );

    return HistoryBaseDaoAdapter
        .getHistoryEntries(employee)
        .stream()
        .filter(hasStatusChangeHistoryEntries)
        .map(HistoryEntry::getModifiedAt)
        .findFirst(); // the history entries are already sorted by date
  }
}
