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
 * Interface to the rulex database.
 */


#ifndef RULEXDB_H
#define RULEXDB_H

#include <regex.h>
#include <db.h>

/* BEGIN_C_DECLS should be used at the beginning of C declarations,
   so that C++ compilers don't mangle their names.  Use END_C_DECLS at
   the end of C declarations. */
#undef BEGIN_C_DECLS
#undef END_C_DECLS
#ifdef __cplusplus
# define BEGIN_C_DECLS extern "C" {
# define END_C_DECLS }
#else
# define BEGIN_C_DECLS /* empty */
# define END_C_DECLS /* empty */
#endif

BEGIN_C_DECLS


/* Constants */

/* Data size limits */
#define RULEXDB_MAX_KEY_SIZE 50
#define RULEXDB_MAX_RECORD_SIZE 200
#define RULEXDB_BUFSIZE 256

/* Return codes */
#define RULEXDB_SUCCESS 0
#define RULEXDB_SPECIAL 1
#define RULEXDB_FAILURE -1
#define RULEXDB_EMALLOC -2
#define RULEXDB_EINVKEY -3
#define RULEXDB_EINVREC -4
#define RULEXDB_EPARM -5
#define RULEXDB_EACCESS -6

/* Search flags */
#define RULEXDB_EXCEPTIONS 1
#define RULEXDB_FORMS 2
#define RULEXDB_RULES 4

/* Data sets */
#define RULEXDB_DEFAULT 0
#define RULEXDB_EXCEPTION 1
#define RULEXDB_LEXBASE 2
#define RULEXDB_LEXCLASS 3
#define RULEXDB_RULE 4
#define RULEXDB_CORRECTOR 5
#define RULEXDB_EXCEPTION_RAW 6
#define RULEXDB_PREFIX 7


/* Data structures */

typedef struct /* Ruleset handler */
{
  DB *db; /* Associated database (dataset) */
  DB_ENV *env; /* Pointer to the database environment */
  const char *db_name; /* Dataset name */
  regex_t **pattern; /* Array of compiled patterns */
  char **replacement; /* Array of replacement strings */
  int nrules; /* Number of rules in the ruleset */
} RULEX_RULESET;

typedef struct /* Lexical database handler */
{
  RULEX_RULESET rules; /* General rules */
  RULEX_RULESET lexclasses; /* Lexical class defining rules */
  RULEX_RULESET prefixes; /* Word prefixes */
  RULEX_RULESET correctors; /* Correction rules */
  DB *lexicon_db; /* Dictionary of lexical bases */
  DB *exceptions_db; /* Dictionary of exceptions */
  DB_ENV *env; /* Pointer to the database environment */
} RULEXDB;


/* Database access routines */

extern RULEXDB *rulexdb_open(const char *path);
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

extern void rulexdb_close(RULEXDB *rulexdb);
/*
 * Close lexical database and free all resources
 * allocated for its sake.
 */

extern int rulexdb_search(RULEXDB *rulexdb, const char * key, char *value, int flags);
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
 */

END_C_DECLS

#endif
