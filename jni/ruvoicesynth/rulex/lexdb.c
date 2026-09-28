/* This file is part of the rulexdb library.
 *
 * Copyright (C) 2006 Igor B. Poretsky <poretsky@mlbox.ru>
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

/*
 * Rulex database access routines.
 */


#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#include <android/log.h>

#include "lexdb.h"
#include "coder.h"


#define LOG_TAG "RuLex"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


/* Local constants */

/* Internal flag for the recursive call of rulexdb_search() */
#define RULEXDB_NOPREFIX 0x80

/* Data storage methods */
#define LEXICON_DB_TYPE DB_BTREE
#define RULES_DB_TYPE DB_RECNO


/* Local data */

/* Datasets names */
static const char *lexicon_db_name = "Lexbases";
static const char *exceptions_db_name = "Exceptions";
static const char *rules_db_name = "General";
static const char *lexclasses_db_name = "Lexclasses";
static const char *prefixes_db_name = "Prefixes";
static const char *corrections_db_name = "Corrections";


/* Local routines */

static DB *db_open(DB_ENV *env, const char *name, int type)
     /*
      * Open DB for specified dataset.
      *
      * Parameters:
      * env - pointer to the database environment
      *       initialized by rulexdb_open();
      * name - the dataset name;
      * type - data storage type;
      *
      * This routine returns pointer to initialized DB handler
      * when success or NULL when failure.
      */
{
  int rc;
  DB *db;

  if (db_create(&db, env, 0))
    return NULL;
  switch (type)
    {
      case DB_RECNO:
	rc = db->set_flags(db, DB_RENUMBER);
	break;
      case DB_BTREE:
	rc = db->set_flags(db, DB_REVSPLITOFF);
	break;
      default:
	rc = 0;
    }
  if (rc)
    {
      (void)db->close(db, 0);
      return NULL;
    }

  rc = db->open(db, NULL, env->app_private, name, type, DB_RDONLY, 0);
  if (rc)
    {
      (void)db->close(db, 0);
      db = NULL;
    }
  return db;
}

static void db_close(DB *db)
     /*
      * Safely close the database.
      */
{
  DBC *dbc = db->app_private;

  if (dbc) /* Close cursor at first if it was opened */
    (void)dbc->c_close(dbc);
  (void)db->close(db, 0);
  return;
}

static int db_get(DB *db, const char *key, char *value)
     /*
      * Retrieve data from dictionary dataset.
      * This routine performs all the work concerning key and value coding.
      * The argument "value" must point to memory area where
      * the resulting string will be placed. This area must have
      * enough space. It will be a copy of key if search fails.
      *
      * This routine returns 0 in the case of success.
      * If specified key doesn't exist, then RULEXDB_SPECIAL is returned.
      * In other cases an appropriate error code will be returned.
      */
{
  int rc;
  char packed_key[RULEXDB_BUFSIZE];
  DBT inKey, inVal;

  (void)memset(&inKey, 0, sizeof(DBT));
  (void)memset(&inVal, 0, sizeof(DBT));
  inKey.size = rulexdb_pack_key(key, packed_key);
  if ((signed int)(inKey.size) <= 0)
    return RULEXDB_EINVKEY;
  inKey.data = packed_key;
  rc = db->get(db, NULL, &inKey, &inVal, 0);
  switch (rc)
    {
      case 0:
	rulexdb_unpack_data(value, inVal.data, inVal.size);
	return RULEXDB_SUCCESS;
      case DB_NOTFOUND:
	return RULEXDB_SPECIAL;
      default:
	break;
    }
  return RULEXDB_FAILURE;
}

static int db_nrecs(DB *db)
     /*
      * Count records in the database.
      * only for rules datasets.
      */
{
  int rc;
  DB_BTREE_STAT *sp;

  rc = db->stat(db, NULL, &sp, DB_FAST_STAT);
  if (rc) return 0;

  rc = sp->bt_nkeys;
  free(sp);
  return rc;
}

static char *rule_get(DB *db, int n)
     /*
      * Retrieve rule by number.
      * This routine returns pointer to the rule text representation
      * when success or NULL when failure.
      * This pointer is valid only until the next database operation.
      */
{
  int rc;
  DBT inKey, inVal;
  db_recno_t recno;

  (void)memset(&inKey, 0, sizeof(DBT));
  (void)memset(&inVal, 0, sizeof(DBT));
  recno = n;
  inKey.data = &recno;
  inKey.size = sizeof(db_recno_t);
  rc = db->get(db, NULL, &inKey, &inVal, 0);
  if (rc)
    return NULL;
  return inVal.data;
}

static int rules_init(RULEX_RULESET *rules)
     /*
      * Initialize ruleset for subsequent fetching and loading
      * (not for updating).
      *
      * This routine at first checks if the ruleset is already initialized
      * and exits successfully if so. If the ruleset appears initialized
      * for updating or failed to initialize earlier,
      * RULEXDB_EACCESS error code will be returned.
      */
{
  if (rules->nrules < 0) /* Cannot be initialized for loading */
    return RULEXDB_EACCESS;
  if (rules->db) /* Already initialized */
    return RULEXDB_SUCCESS;
  rules->db = db_open(rules->env, rules->db_name,
		      RULES_DB_TYPE);
  if (!rules->db) /* DB open failed, so smudge the ruleset for future. */
    {
      rules->nrules = -1;
      return RULEXDB_FAILURE;
    }
  rules->nrules = db_nrecs(rules->db);
  if (rules->nrules > 0) /* Not empty */
    {
      /* Allocate memory for pointers */
      rules->pattern = calloc(rules->nrules, sizeof(regex_t *));
      if (!rules->pattern)
	{
	  db_close(rules->db);
	  rules->db = NULL;
	  rules->nrules = -1;
	  return RULEXDB_EMALLOC;
	}
      rules->replacement = calloc(rules->nrules, sizeof(char *));
      if (!rules->replacement)
	{
	  free(rules->pattern);
	  db_close(rules->db);
	  rules->db = NULL;
	  rules->nrules = -1;
	  return RULEXDB_EMALLOC;
	}
    }
  return RULEXDB_SUCCESS;
}

static int rule_load(RULEX_RULESET *rules, int n)
     /*
      * Preload specified rule and turn it into internal representation.
      *
      * This routine at first checks if specified rule is already loaded
      * and exits successfully if so.
      *
      * The ruleset itself must be initialized before.
      */
{
  int rc;
  char *s, *rule_src;

  if (n >= rules->nrules) /* Specified rule number validation */
    return RULEXDB_EPARM;
  if (rules->pattern[n]) /* Already loaded */
    return RULEXDB_SUCCESS;

  /* Get rule source */
  rule_src = rule_get(rules->db, n + 1);
  if (!rule_src)
    return RULEXDB_FAILURE;

  /* Allocate memory for compiled pattern */
  rules->pattern[n] = calloc(1, sizeof(regex_t));
  if (!rules->pattern[n])
    return RULEXDB_EMALLOC;

  /* Compile pattern */
  rc = regcomp(rules->pattern[n], strtok(rule_src, " "),
	       REG_EXTENDED | REG_ICASE);
  if (rc) /* Pattern compiling failure */
    {
      regfree(rules->pattern[n]);
      free(rules->pattern[n]);
      rules->pattern[n] = NULL;
      return RULEXDB_FAILURE;
    }

  /* Save replacement if needed */
  s = strtok(NULL, " ");
  if (s)
    rules->replacement[n] = strdup(s);

  return RULEXDB_SUCCESS;
}

static void rules_release(RULEX_RULESET *rules)
     /*
      * Release the ruleset and free all resources allocated
      * for its sake.
      */
{
  int i;

  for (i = 0; i < rules->nrules; i++)
    {
      if (rules->pattern[i])
	{
	  regfree(rules->pattern[i]);
	  free(rules->pattern[i]);
	  rules->pattern[i] = NULL;
	}
      if (rules->replacement[i])
	{
	  free(rules->replacement[i]);
	  rules->replacement[i] = NULL;
	}
    }
  free(rules->pattern);
  rules->pattern = NULL;
  free(rules->replacement);
  rules->replacement = NULL;
  if (rules->db)
    {
      db_close(rules->db);
      rules->db = NULL;
    }
  rules->nrules = 0;
  return;
}

static int lexguess(RULEXDB *rulexdb, const char *s, char *t)
     /*
      * This routine tries to guess stressing for the word
      * pointed by s by general rules from the database. If success,
      * the result is placed into memory area pointed by t,
      * which must have enough space for it.
      *
      * Return value indicates whether the guessing succeeded or not.
      * If no rule has matched, then RULEXDB_SPECIAL is returned.
      */
{
  int i;
  regmatch_t match[2];

  i = rules_init(&rulexdb->rules);
  if (i) return i;

  for (i = 0; i < rulexdb->rules.nrules; i++)
    if (!rule_load(&rulexdb->rules, i))
      if (!regexec(rulexdb->rules.pattern[i], s, 2, match, 0))
	{
	  (void)strncpy(t, s, match[1].rm_eo);
	  t[match[1].rm_eo] = '+';
	  (void)strcpy(t + match[1].rm_eo + 1, s + match[1].rm_eo);
	  return RULEXDB_SUCCESS;
	}
  return RULEXDB_SPECIAL;
}

static int postcorrect(RULEXDB *rulexdb, char *s)
     /*
      * This routine performs some additional word corrections
      * according to the correction rules from the database if needed.
      */
{
  int i, k, l;
  char *r, *t, *orig;
  regmatch_t match[10];

  i = rules_init(&rulexdb->correctors);
  if (i) return i;

  for (i = 0; i < rulexdb->correctors.nrules; i++)
    if (!rule_load(&rulexdb->correctors, i))
      if (!regexec(rulexdb->correctors.pattern[i], s, 10, match, 0))
	{
	  t = s + match[0].rm_so;
	  orig = strdup(t);
	  if (!orig) return RULEXDB_EMALLOC;
	  for (r = rulexdb->correctors.replacement[i]; *r; r++)
	    if (((*r) >= '0') && ((*r) <= '9'))
	      {
		k = (*r) - '0';
		l = match[k].rm_eo - match[k].rm_so;
		if (l)
		  {
		    (void)strncpy(t, orig + match[k].rm_so - match[0].rm_so, l);
		    t += l;
		  }
	      }
	    else *t++ = *r;
	  l = strlen(orig) + match[0].rm_so - match[0].rm_eo;
	  if (l)
	    {
	      (void)strcpy(t, orig + match[0].rm_eo - match[0].rm_so);
	      t += l;
	    }
	  *t = 0;
	  free(orig);
	}
  return RULEXDB_SUCCESS;
}

static int rulexdb_lexbase(RULEXDB *rulexdb, const char *s, char *t, int n)
     /*
      * Try to find lexical base for the specified word.
      *
      * This routine scans lexclasses ruleset beginning from n
      * trying to match the word pointed by s. When match succeeds,
      * the lexical base is constructed in memory area pointed by t,
      * which must have enough space for it, and the number of matched rule
      * is returned. If no match has occurred 0 is returned.
      * In the case of error an appropriate error code is returned.
      */
{
  int i, rc;
  regmatch_t match[2];

  if ((n < 1) || (!rulexdb) || (!s) || (!t)) return RULEXDB_EPARM;
  rc = rules_init(&rulexdb->lexclasses);
  if (rc) return rc;

  for (i = n - 1; i < rulexdb->lexclasses.nrules; i++)
    if ((rc = rule_load(&rulexdb->lexclasses, i)))
      break;
    else if (!regexec(rulexdb->lexclasses.pattern[i], s, 2, match, 0))
      {
	(void)strncpy(t, s, match[1].rm_eo);
	t[match[1].rm_eo] = 0;
	if (rulexdb->lexclasses.replacement[i])
	  (void)strcat(t, rulexdb->lexclasses.replacement[i]);
	rc = i + 1;
	break;
      }
  return rc;
}

static int rulexdb_classify(RULEXDB *rulexdb, const char *s)
     /*
      * Test specified word whether it represents a lexical base.
      *
      * Returns 0 (RULEXDB_SUCCESS) when the word does not represent
      * a lexical base, RULEXDB_SPECIAL if it does, or an appropriate
      * error code when failure.
      */
{
  int i;
  char *t = malloc(strlen(s) + 32);

  if (!t)
    return RULEXDB_EMALLOC;
  for (i = 1; i > 0; i++)
    {
      i = rulexdb_lexbase(rulexdb, s, t, i);
      if (i < 0)
	{
	  free(t);
	  return i;
	}
      if (i)
	{
	  if (!strcmp(s, t))
	    {
	      free(t);
	      return RULEXDB_SPECIAL;
	    }
	}
      else i--;
    }
  free(t);
  return RULEXDB_SUCCESS;
}

static DB **choose_dictionary(RULEXDB *rulexdb, const char *key, int item_type)
     /*
      * Choose the dictionary and open it if necessary.
      * If item_type specified as RULEXDB_DEFAULT, then choosing is based
      * on key: if key is recognized as a lexical base,
      * then the lexbases dictionary is chosen, otherwise exceptions
      * dictionary is used.
      *
      * Returns pointer to the DB handler when success
      * or NULL when failure.
      */
{
  const char *db_name;
  DB **db;

  if (!rulexdb) return NULL;
  switch (item_type)
    {
      case RULEXDB_EXCEPTION:
      case RULEXDB_EXCEPTION_RAW:
	db = &rulexdb->exceptions_db;
	db_name = exceptions_db_name;
	break;
      case RULEXDB_LEXBASE:
	db = &rulexdb->lexicon_db;
	db_name = lexicon_db_name;
	break;
      case RULEXDB_DEFAULT:
	if (key)
	  {
	    if (rulexdb_classify(rulexdb, key) == RULEXDB_SPECIAL)
	      {
		db = &rulexdb->lexicon_db;
		db_name = lexicon_db_name;
	      }
	    else
	      {
		db = &rulexdb->exceptions_db;
		db_name = exceptions_db_name;
	      }
	  }
	else return NULL;
	break;
      default:
	return NULL;
    }
  if (!(*db))
    *db = db_open(rulexdb->env, db_name, LEXICON_DB_TYPE);
  return db;
}


/* Externally visible routines */

RULEXDB *rulexdb_open(const char *path)
     /*
      * Open lexical database.
      *
      * This routine does not actually open any dataset
      * (the datasets are to be opened later by demand),
      * but it allocates and initializes new RULEXDB structure
      * and opens the database environment. By the way
      * this routine checks accessibility of the database file.
      *
      * Arguments description:
      * path - path to the database file;
      *
      * Returns pointer to the new RULEXDB structure when success
      * or NULL otherwise.
      */
{
  RULEXDB *rulexdb = calloc(1, sizeof(RULEXDB));
  int rc;

  if (!rulexdb)
    return NULL;

  /* Create database environment */
  rc = db_env_create(&rulexdb->env, 0);
  if (rc)
    {
      LOGE("Database environment creation failed: %s", db_strerror(rc));
      free(rulexdb);
      return NULL;
    }

  /* Open it */
  rc = rulexdb->env->open(rulexdb->env, NULL,
                     DB_INIT_MPOOL | DB_CREATE | DB_PRIVATE,
                     0);
  if (rc)
    {
      LOGE("Opening database environment failed: %s", db_strerror(rc));
      (void)rulexdb->env->close(rulexdb->env, 0);
      free(rulexdb);
      return NULL;
    }

  /* Initialize necessary RULEXDB fields */
  rulexdb->env->app_private = (char *)path;
  rulexdb->rules.env = rulexdb->env;
  rulexdb->rules.db_name = rules_db_name;
  rulexdb->lexclasses.env = rulexdb->env;
  rulexdb->lexclasses.db_name = lexclasses_db_name;
  rulexdb->prefixes.env = rulexdb->env;
  rulexdb->prefixes.db_name = prefixes_db_name;
  rulexdb->correctors.env = rulexdb->env;
  rulexdb->correctors.db_name = corrections_db_name;

  /* Check database file accessibility */
  if (access(path, F_OK | R_OK))
    {
      LOGE("Database file is not accessible");
      (void)rulexdb->env->close(rulexdb->env, 0);
      free(rulexdb);
      rulexdb = NULL;
    }

  return rulexdb;
}

void rulexdb_close(RULEXDB *rulexdb)
     /*
      * Close lexical database and free all resources
      * allocated for its sake.
      */
{
  rules_release(&rulexdb->rules);
  rules_release(&rulexdb->lexclasses);
  rules_release(&rulexdb->prefixes);
  rules_release(&rulexdb->correctors);
  if (rulexdb->lexicon_db)
    db_close(rulexdb->lexicon_db);
  if (rulexdb->exceptions_db)
    db_close(rulexdb->exceptions_db);
  (void)rulexdb->env->close(rulexdb->env, 0);
  free(rulexdb);
  return;
}

int rulexdb_search(RULEXDB *rulexdb, const char * key, char *value, int flags)
     /*
      * Search lexical database for specified word.
      *
      * This routine searches lexical database and tries to guess
      * pronunciation of specified word according to the acquired info.
      * The resulting string is placed into the buffer pointed
      * by value. This buffer must have enough space for it.
      * When no useful info is found, the original word (key)
      * is copied to the value buffer and RULEXDB_SPECIAL code
      * is returned.
      *
      * Searching is performed in the following order:
      * Specified word is searched in the exceptions dictionary.
      * If found, the result is returned and procedure
      * exits successfully. Otherwise the word is treated
      * as an implicit form and program tries to guess its base
      * and find it in the lexbases dictionary. If this process
      * succeeds, the pronunciation string is constructed
      * according to the acquired data and procedure exits successfully.
      * At last, the word is matched against general rules.
      * If no match succeeds, then program exits with RULEXDB_SPECIAL code,
      * returning original word as a result.
      *
      * If this process appears to be not fully unsuccessful and some
      * information was found in the database, then the resulting string
      * is matched against correction rules and the first matched one
      * is applied if any.
      *
      * When no information is found, the word is matched against
      * prefix rules and the process is repeated for the word stem
      * with the matched prefix stripped off.
      *
      * The last argument specifies which steps of the described
      * process are to be performed. It consists of following flags
      * which may be joined by "or" operation:
      * RULEXDB_EXCEPTIONS - search the word in the exceptions dictionary.
      * RULEXDB_FORMS - try to treat specified word as an implicit form.
      * RULEXDB_RULES - try to apply general rules.
      * Zero value (no flags) means that full search (all stages)
      * should be performed.
      *
      * RULEXDB_NOPREFIX - internal flag used in the recursive call
      * for the words with prefix stripped.
      */
{
  int i, j, rc = RULEXDB_SPECIAL;
  char *s;
  DB **db;

  (void)strcpy(value, key);

  /* The first stage: looking up in the exceptions dictionary */
  if ((!flags) || (flags & RULEXDB_EXCEPTIONS))
    {
      db = choose_dictionary(rulexdb, NULL, RULEXDB_EXCEPTION);
      if (!db) return RULEXDB_EPARM;
      if (*db)
	{
	  rc = db_get(*db, key, value);
	  if (rc < 0) return rc;
	}
    }

  /* The second stage: treating the word as an implicit form */
  if ((rc == RULEXDB_SPECIAL) && ((!flags) || (flags & RULEXDB_FORMS)))
    {
      db = choose_dictionary(rulexdb, NULL, RULEXDB_LEXBASE);
      if (!db) return RULEXDB_EPARM;
      if (*db)
	{
	  s = malloc(strlen(key) + 32);
	  if (s)
	    for (i = 1; rc == RULEXDB_SPECIAL; i++)
	      {
		i = rulexdb_lexbase(rulexdb, key, s, i);
		if (!i) break;
		if (i < 0)
		  {
		    free(s);
		    return i;
		  }
		if (strlen(key) < strlen(s))
		  {
		    for (j = strlen(key); j < strlen(s); j++)
		      value[j] ='_';
		    value[strlen(s)] = 0;
		  }
		else value[strlen(key)] = 0;
		rc = db_get(*db, s, value);
		if (rc < 0)
		  {
		    free(s);
		    return rc;
		  }
	      }
	  else return RULEXDB_EMALLOC;
	  free(s);
	}

      /* Prefix detection stage */
      if ((rc == RULEXDB_SPECIAL) &&
          !rules_init(&rulexdb->prefixes))
        {
          s = malloc(strlen(key));
          if (s)
            {
              regmatch_t match;
              for (i = 0; (rc == RULEXDB_SPECIAL) && (i < rulexdb->prefixes.nrules); i++)
                if ((!rule_load(&rulexdb->prefixes, i)) &&
                    (!regexec(rulexdb->prefixes.pattern[i], key, 1, &match, 0)) &&
                    (!match.rm_so) &&
                    (match.rm_eo < strlen(key)))
                  {
                    if (rulexdb->prefixes.replacement[i])
                      (void)strcpy(s, rulexdb->prefixes.replacement[i]);
                    else *s = 0;
                    j = strlen(s);
                    (void)strcat(s, key + match.rm_eo);
                    rc = rulexdb_search(rulexdb, s, value + match.rm_eo - j, RULEXDB_FORMS | RULEXDB_NOPREFIX);
                    if (rc == RULEXDB_EINVKEY)
                      rc = RULEXDB_SPECIAL;
                    (void)strncpy(value, key, match.rm_eo);
                  }
              free(s);
            }
          else rc = RULEXDB_EMALLOC;
        }
    }

  /* The last resort: trying to use a general rule */
  if (rc == RULEXDB_SPECIAL)
    {
      value[strlen(key)] = 0;
      if ((!flags) || (flags & RULEXDB_RULES))
	rc = lexguess(rulexdb, key, value);
    }

  /* Applying a post-correction if needed */
  if (!rc && !(flags & RULEXDB_NOPREFIX))
    rc = postcorrect(rulexdb, value);

  return rc;
}
