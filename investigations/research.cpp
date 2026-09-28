#include <string>
#include <iostream>
#include <fstream>
#include <locale>
#include <algorithm>
#include <exception>

#include <cstdlib>
#include <cstring>
#include <cwchar>
#include <unistd.h>
#include <db_cxx.h>


using namespace std;

#define MAXLANGNAMELEN 12

typedef struct
{
  size_t score;
  char language[MAXLANGNAMELEN];
} ItemInfo;

typedef struct
{
  size_t starts;
  size_t middles;
  size_t ends;
  size_t total;
} StatInfo;

typedef struct
{
  size_t starts;
  size_t middles;
  size_t ends;
  size_t total;
  char language[MAXLANGNAMELEN];
} ExtendedInfo;

typedef struct
{
  const char* name;
  const wchar_t* symbols;
  wstring uniques;
} Language;

enum ActionMode
  {
    ENTIRE,
    START,
    MIDDLE,
    END,
    SELECTION,
    STATS,
    RESEARCH
  };

static const char* duplications = "duplications";
static const char* dbFile = "research.db";

static const char* extFreq = ".frequent";
static const char* extStat = ".stat";

static const char* extFreqSrc = ".freqs";
static const char* extRank = ".ranks";

static const char* extHeads = ".heads";
static const char* extMiddles = ".middles";
static const char* extTails = ".tails";

static const char* extUnique = ".unique";
static const char* extTop = ".top";

static const char* extLeft = ".left";
static const char* extRight = ".right";
static const char* extDual = ".dual";

static const char* utf8 = "en_US.UTF-8";
static const locale unicode(utf8);

static Language languages[] =
  {
    {
      "German",
      L"äöüß",
      L""
    },
    {
      "Italian",
      L"àòùìèé",
      L""
    },
    {
      "Spanish",
      L"ñáéíóúü",
      L""
    },
    {
      "French",
      L"àâæçéèêëîïôœùûüÿ",
      L""
    },
    {
      "Portuguese",
      L"áâãàçéêíóôõúü",
      L""
    },
    {
      "Polish",
      L"ąćęłńóśźż",
      L""
    },
    {
      "Croatian",
      L"čćđšž",
      L""
    },
    {
      "Turkish",
      L"çğİıöşüâîû",
      L""
    }
  };

static bool checkItem(Db& db, ActionMode mode, const wstring& s, const wstring& specials)
{
  for (int i = 0; i < specials.length(); i++)
    if (wcschr(s.c_str(), specials[i]))
      return false;
  if (s.length() > 2)
    {
      Dbt key;
      switch (mode)
        {
        case START:
          for (int n = 2; n < s.length(); n++)
            {
              key.set_data(const_cast<wchar_t*>(s.c_str()));
              key.set_size(n * sizeof(wchar_t));
              if (db.exists(NULL, &key, 0) != DB_NOTFOUND)
                return false;
            }
          break;
        case MIDDLE:
          for (int n = 2; n < s.length(); n++)
            {
              key.set_size(n * sizeof(wchar_t));
              for (int k = 0; k <= (s.length() - n); k++)
                {
                  key.set_data(const_cast<wchar_t*>(s.c_str()) + k);
                  if (db.exists(NULL, &key, 0) != DB_NOTFOUND)
                    return false;
                }
            }
          break;
        case END:
          for (int n = 2; n < s.length(); n++)
            {
              key.set_data(const_cast<wchar_t*>(s.c_str()) + s.length() - n);
              key.set_size(n * sizeof(wchar_t));
              if (db.exists(NULL, &key, 0) != DB_NOTFOUND)
                return false;
            }
          break;
        default:
          break;
        }
    }
  return true;
}

static bool addItem(Db& db, wstring& s, const char* lang, bool consider)
{
  ItemInfo itemInfo;
  Dbt key, data;

  itemInfo.score = 1;
  memset(itemInfo.language, 0, MAXLANGNAMELEN);
  strncpy(itemInfo.language, lang, MAXLANGNAMELEN);
  key.set_data(const_cast<wchar_t*>(s.c_str()));
  key.set_size(s.length() * sizeof(wchar_t));
  data.set_data(&itemInfo);
  data.set_size(sizeof(ItemInfo));
  if (db.put(NULL, &key, &data, DB_NOOVERWRITE) == DB_KEYEXIST)
    {
      data.set_data(&itemInfo);
      data.set_ulen(sizeof(ItemInfo));
      data.set_flags(DB_DBT_USERMEM);
      db.get(NULL, &key, &data, 0);
      if (strncmp(lang, itemInfo.language, MAXLANGNAMELEN))
        return false;
      else if (consider)
        {
          itemInfo.score++;
          db.put(NULL, &key, &data, 0);
        }
    }
  return true;
}

static char* getLangName(char* filename)
{
  char* lang = strrchr(filename, '/');
  return lang ? ++lang : filename;
}

static void cleanup(Db& db)
{
  Dbt key;
  wstring ws;
  wifstream infile(duplications);
  infile.imbue(unicode);
  while (getline(infile, ws))
    {
      key.set_data(const_cast<wchar_t*>(ws.c_str()));
      key.set_size(ws.length() * sizeof(wchar_t));
      db.del(NULL, &key, 0);
    }
  infile.close();
}

static const char* getext(ActionMode mode)
{
  switch (mode)
    {
    case START:
      return extHeads;
    case MIDDLE:
      return extMiddles;
    case END:
      return extTails;
    default:
      break;
    }
  return extUnique;
}

static void outputresult(Db& db, ActionMode mode, const char* useFreq, int argc, char* argv[])
{
  Dbt key, data;
  Dbc* cursor;
  wofstream outfile;
  wstring ws;

  outfile.imbue(unicode.combine< numpunct<wchar_t> >(locale::classic()));
  for (int i = optind; i < argc; i++)
    {
      ItemInfo itemInfo;
      char* lang = getLangName(argv[i]);
      string s(argv[i]);
      s += getext(mode);
      if (strlen(useFreq))
        {
          if (strcmp(useFreq, lang))
            continue;
          else s += extFreq;
        }
      outfile.open(s.c_str(), ios::trunc);
      db.cursor(NULL, &cursor, 0);
      data.set_data(&itemInfo);
      data.set_ulen(sizeof(ItemInfo));
      data.set_flags(DB_DBT_USERMEM);
      while (!cursor->get(&key, &data, DB_NEXT))
        if (!strncmp(itemInfo.language, lang, MAXLANGNAMELEN))
          {
            ws.assign(reinterpret_cast<wchar_t*>(key.get_data()), key.get_size() / sizeof(wchar_t));
            if (mode != ENTIRE)
              outfile << itemInfo.score << ' ' << ws << endl;
            else outfile << ws << endl;
          }
      cursor->close();
      outfile.close();
      if (mode == ENTIRE)
        {
          wifstream infile;
          size_t freq;
          s += extRank;
          outfile.open(s.c_str(), ios::trunc);
          s.assign(argv[i]);
          s += extFreqSrc;
          infile.imbue(unicode);
          infile.open(s.c_str());
          while (!(infile >> ws >> freq).eof())
            {
              key.set_data(const_cast<wchar_t*>(ws.c_str()));
              key.set_size(ws.length() * sizeof(wchar_t));
              if ((db.get(NULL, &key, &data, 0) != DB_NOTFOUND) &&
                  !strncmp(itemInfo.language, lang, MAXLANGNAMELEN))
                outfile << ws << ' ' << freq << endl;
            }
          infile.close();
          outfile.close();
        }
    }
}

static void finduniques(Db& db, ActionMode mode, int itemLen, const char* useFreq, int argc, char* argv[])
{
  Dbt key, data;
  wifstream infile;
  wofstream outfile;
  wstring ws;

  infile.imbue(unicode);
  outfile.imbue(unicode.combine< numpunct<wchar_t> >(locale::classic()));

  outfile.open(duplications, ios::trunc);
  for (int i = optind; i < argc; i++)
    {
      wstring specials;
      char* lang = getLangName(argv[i]);
      string s(argv[i]);
      bool consider = !strlen(useFreq);
      if (!strcmp(lang, useFreq))
        {
          s += extFreq;
          consider = true;
        }
      for (int k = 0; k < (sizeof(languages) / sizeof(Language)); k++)
        if (!strcmp(lang, languages[k].name))
          {
            specials = languages[k].uniques;
            break;
          }
      infile.open(s.c_str());
      while (getline(infile, ws))
        {
          transform(ws.begin(), ws.end(), ws.begin(), towlower);
          switch (mode)
            {
            case START:
              if (ws.length() > itemLen)
                {
                  wstring item(ws, 0, itemLen);
                  if (checkItem(db, mode, item, specials))
                    if (!addItem(db, item, lang, consider))
                      outfile << item << endl;
                }
              break;
            case MIDDLE:
              if (ws.length() > (itemLen + 1))
                {
                  wstring item;
                  int n = ws.length() - itemLen;
                  for (int k = 1; k < n; k++)
                    {
                      item.assign(ws, k, itemLen);
                      if (checkItem(db, mode, item, specials))
                        if (!addItem(db, item, lang, consider))
                          outfile << item << endl;
                    }
                }
              break;
            case END:
              if (ws.length() > itemLen)
                {
                  wstring item;
                  item.assign(ws, ws.length() - itemLen, itemLen);
                  if (checkItem(db, mode, item, specials))
                    if (!addItem(db, item, lang, consider))
                      outfile << item << endl;
                }
              break;
            default:
              if (!addItem(db, ws, lang, consider))
                outfile << ws << endl;
              break;
            }
        }
      infile.close();
    }
  outfile.close();

  cleanup(db);
}

static void outputstats(wofstream& outfile, wstring& ws, StatInfo& info)
{
  outfile << info.total << ' ' << ws << ' ' << info.starts << ' ' << info.middles << ' ' << info.ends << endl;
}

static void getstats(int maxlen, int argc, char* argv[])
{
  StatInfo info;
  Dbt key, data;
  Dbc* cursor;
  wifstream infile;
  wofstream outfile;
  wstring ws;

  infile.imbue(unicode);
  outfile.imbue(unicode.combine< numpunct<wchar_t> >(locale::classic()));

  for (int i = optind; i < argc; i++)
    {
      Db db(NULL, 0);
      db.set_error_stream(&cerr);
      db.open(NULL, dbFile, NULL, DB_BTREE, DB_CREATE | DB_TRUNCATE, 0);
      infile.open(argv[i]);
      while (getline(infile, ws))
        if (ws.length() > 1)
          {
            transform(ws.begin(), ws.end(), ws.begin(), towlower);
            for (int l = 2; l <= maxlen; l++)
              {
                int n = ws.length() - l;
                for (int k = 0; k <= n; k++)
                  {
                    if (!k)
                      {
                        info.starts = 1;
                        info.middles = 0;
                        info.ends = 0;
                      }
                    else if (k < n)
                      {
                        info.starts = 0;
                        info.middles = 1;
                        info.ends = 0;
                      }
                    else
                      {
                        info.starts = 0;
                        info.middles = 0;
                        info.ends = 1;
                      }
                    info.total = 1;
                    key.set_data(const_cast<wchar_t*>(ws.c_str()) + k);
                    key.set_size(l * sizeof(wchar_t));
                    data.set_data(&info);
                    data.set_size(sizeof(StatInfo));
                    if (db.put(NULL, &key, &data, DB_NOOVERWRITE) == DB_KEYEXIST)
                      {
                        data.set_data(&info);
                        data.set_ulen(sizeof(StatInfo));
                        data.set_flags(DB_DBT_USERMEM);
                        db.get(NULL, &key, &data, 0);
                        if (!k)
                          info.starts++;
                        else if (k < n)
                          info.middles++;
                        else info.ends++;
                        info.total++;
                        db.put(NULL, &key, &data, 0);
                      }
                  }
              }
          }
      infile.close();
      string stats(argv[i]);
      stats += extStat;
      outfile.open(stats.c_str(), ios::trunc);
      db.cursor(NULL, &cursor, 0);
      data.set_data(&info);
      data.set_ulen(sizeof(StatInfo));
      data.set_flags(DB_DBT_USERMEM);
      while (!cursor->get(&key, &data, DB_NEXT))
        {
          ws.assign(reinterpret_cast<wchar_t*>(key.get_data()), key.get_size() / sizeof(wchar_t));
          outputstats(outfile, ws, info);
        }
      cursor->close();
      outfile.close();
      db.close(0);
    }
}

static void research(int maxlen, const char* useFreq, int argc, char* argv[])
{
  Db db(NULL, 0);
  Dbt key, data;
  Dbc* cursor;
  wifstream infile;
  wofstream outfile;
  wstring ws;
  ExtendedInfo info;

  infile.imbue(unicode);
  outfile.imbue(unicode.combine< numpunct<wchar_t> >(locale::classic()));
  db.set_error_stream(&cerr);
  db.open(NULL, dbFile, NULL, DB_BTREE, DB_CREATE | DB_TRUNCATE, 0);

  outfile.open(duplications, ios::trunc);
  for (int i = optind; i < argc; i++)
    {
      wstring specials;
      char* lang = getLangName(argv[i]);
      string s(argv[i]);
      bool consider = !strlen(useFreq);
      if (!strcmp(lang, useFreq))
        {
          s += extFreq;
          consider = true;
        }
      for (int k = 0; k < (sizeof(languages) / sizeof(Language)); k++)
        if (!strcmp(lang, languages[k].name))
          {
            specials = languages[k].uniques;
            break;
          }
      infile.open(s.c_str());
      while (getline(infile, ws))
        if (ws.length() > 1)
          {
            transform(ws.begin(), ws.end(), ws.begin(), towlower);
            for (int l = 2; l <= maxlen; l++)
              {
                int n = ws.length() - l;
                for (int k = 0; k <= n; k++)
                  {
                    wstring item(ws, k, l);
                    if (!checkItem(db, RESEARCH, item, specials))
                      continue;
                    if (!k)
                      {
                        info.starts = 1;
                        info.middles = 0;
                        info.ends = 0;
                      }
                    else if (k < n)
                      {
                        info.starts = 0;
                        info.middles = 1;
                        info.ends = 0;
                      }
                    else
                      {
                        info.starts = 0;
                        info.middles = 0;
                        info.ends = 1;
                      }
                    info.total = 1;
                    memset(info.language, 0, MAXLANGNAMELEN);
                    strncpy(info.language, lang, MAXLANGNAMELEN);
                    key.set_data(const_cast<wchar_t*>(item.c_str()));
                    key.set_size(l * sizeof(wchar_t));
                    data.set_data(&info);
                    data.set_size(sizeof(ExtendedInfo));
                    if (db.put(NULL, &key, &data, DB_NOOVERWRITE) == DB_KEYEXIST)
                      {
                        data.set_data(&info);
                        data.set_ulen(sizeof(ExtendedInfo));
                        data.set_flags(DB_DBT_USERMEM);
                        db.get(NULL, &key, &data, 0);
                        if (strncmp(lang, info.language, MAXLANGNAMELEN))
                          outfile << item << endl;
                        else if (consider)
                          {
                            if (!k)
                              info.starts++;
                            else if (k < n)
                              info.middles++;
                            else info.ends++;
                            info.total++;
                            db.put(NULL, &key, &data, 0);
                          }
                      }
                  }
              }
          }
      infile.close();
    }
  outfile.close();
  cleanup(db);

  for (int i = optind; i < argc; i++)
    {
      char* lang = getLangName(argv[i]);
      string s(argv[i]);
      if (strlen(useFreq))
        {
          if (strcmp(useFreq, lang))
            continue;
          else s += extFreq;
        }
      s += extStat;
      outfile.open(s.c_str(), ios::trunc);
      db.cursor(NULL, &cursor, 0);
      data.set_data(&info);
      data.set_ulen(sizeof(ExtendedInfo));
      data.set_flags(DB_DBT_USERMEM);
      while (!cursor->get(&key, &data, DB_NEXT))
        if (!strncmp(info.language, lang, MAXLANGNAMELEN))
          {
            ws.assign(reinterpret_cast<wchar_t*>(key.get_data()), key.get_size() / sizeof(wchar_t));
            outfile << info.total << ' ' << ws << ' ' << info.starts << ' ' << info.middles << ' ' << info.ends << endl;
          }
      cursor->close();
      outfile.close();
    }
  db.close(0);
}

static void collect(Db& db, ActionMode mode, const char* langFile)
{
  Dbt key, data;
  StatInfo info;
  size_t count;
  wifstream infile;
  wstring ws;
  string filename(langFile);

  infile.imbue(unicode);
  filename += getext(mode);
  infile.open(filename.c_str());
  while (getline((infile >> count).ignore(), ws))
    {
      key.set_data(const_cast<wchar_t*>(ws.c_str()));
      key.set_size(ws.length() * sizeof(wchar_t));
      data.set_data(&info);
      data.set_ulen(sizeof(StatInfo));
      data.set_flags(DB_DBT_USERMEM);
      if (db.get(NULL, &key, &data, 0) == DB_NOTFOUND)
        {
          info.starts = 0;
          info.middles = 0;
          info.ends = 0;
        }
      switch (mode)
        {
        case START:
          info.starts = count;
          break;
        case MIDDLE:
          info.middles = count;
          break;
        case END:
          info.ends = count;
          break;
        default:
          break;
        }
      info.total = info.starts + info.middles + info.ends;
      data.set_size(sizeof(StatInfo));
      db.put(NULL, &key, &data, 0);
    }
  infile.close();
}

static void makeselections(const char* langFile, bool topOnly)
{
  StatInfo info;
  wifstream infile;
  wofstream outleft;
  wofstream outright;
  wofstream outdual;
  wstring ws;
  string filename(langFile);
  Dbt key, data;
  Db db(NULL, 0);

  db.set_error_stream(&cerr);
  db.open(NULL, dbFile, NULL, DB_BTREE, DB_CREATE | DB_TRUNCATE, 0);

  collect(db, START, langFile);
  collect(db, MIDDLE, langFile);
  collect(db, END, langFile);

  infile.imbue(unicode);
  outleft.imbue(unicode.combine< numpunct<wchar_t> >(locale::classic()));
  outright.imbue(unicode.combine< numpunct<wchar_t> >(locale::classic()));
  outdual.imbue(unicode.combine< numpunct<wchar_t> >(locale::classic()));

  filename += extUnique;
  if (topOnly)
    filename += extFreq;
  infile.open(filename.c_str());
  filename.assign(langFile);
  filename += extLeft;
  if (topOnly)
    filename += extTop;
  outleft.open(filename.c_str(), ios::trunc);
  filename.assign(langFile);
  filename += extRight;
  if (topOnly)
    filename += extTop;
  outright.open(filename.c_str(), ios::trunc);
  filename.assign(langFile);
  filename += extDual;
  if (topOnly)
    filename += extTop;
  outdual.open(filename.c_str(), ios::trunc);

  while (getline(infile, ws))
    {
      key.set_data(const_cast<wchar_t*>(ws.c_str()));
      key.set_size(ws.length() * sizeof(wchar_t));
      data.set_data(&info);
      data.set_ulen(sizeof(StatInfo));
      data.set_flags(DB_DBT_USERMEM);
      if (db.get(NULL, &key, &data, 0) != DB_NOTFOUND)
        {
          if (info.starts && info.middles && info.ends)
            outputstats(outdual, ws, info);
          else
            {
              if (info.starts >= (info.middles + info.ends))
                {
                  info.total = info.starts;
                  outputstats(outleft, ws, info);
                }
              if (info.ends >= (info.starts + info.middles))
                {
                  info.total = info.ends;
                  outputstats(outright, ws, info);
                }
            }
        }
    }

  infile.close();
  outleft.close();
  outright.close();
  outdual.close();
}

static void postprocess(Db& db, ActionMode mode, const char* langFile)
{
  Dbt key, data;
  size_t fScore, gScore;
  wifstream infile;
  wofstream outfile;
  wstring ws;
  string filename(langFile);

  infile.imbue(unicode);
  outfile.imbue(unicode.combine< numpunct<wchar_t> >(locale::classic()));

  filename += getext(mode);
  infile.open(filename.c_str());
  while (getline((infile >> gScore).ignore(), ws))
    {
      key.set_data(const_cast<wchar_t*>(ws.c_str()));
      key.set_size(ws.length() * sizeof(wchar_t));
      data.set_data(&gScore);
      data.set_size(sizeof(size_t));
      db.put(NULL, &key, &data, 0);
    }
  infile.close();

  filename.assign(langFile);
  filename += getext(mode);
  filename += extFreq;
  infile.open(filename.c_str());
  filename.assign(langFile);
  filename += getext(mode);
  filename += extTop;
  outfile.open(filename.c_str(), ios::trunc);
  while (getline((infile >> fScore).ignore(), ws))
    {
      key.set_data(const_cast<wchar_t*>(ws.c_str()));
      key.set_size(ws.length() * sizeof(wchar_t));
      data.set_data(&gScore);
      data.set_ulen(sizeof(size_t));
      data.set_flags(DB_DBT_USERMEM);
      if (db.get(NULL, &key, &data, 0) != DB_NOTFOUND)
        outfile << gScore << ' ' << fScore << ' ' << ws << endl;
    }
  outfile.close();
  infile.close();
}

static void preparelangs(int length, Language langs[])
{
    for (int i = 0; i < length; i++)
    {
      int n = wcslen(langs[i].symbols);
      for (int k = 0; k < n; k++)
        {
          int j;
          wchar_t c = langs[i].symbols[k];
          for (j = 0; j < length; j++)
            if ((i != j) && wcschr(langs[j].symbols, c))
              break;
          if (j >= length)
            langs[i].uniques += c;
        }
    }
}

int main(int argc, char* argv[])
{
  ActionMode mode = ENTIRE;
  const char* language = "";
  int length = 0;
  bool exactLen = false;
  bool topOnly = false;

  setlocale(LC_CTYPE, utf8);

  for (int opt = 0; opt != -1; opt = getopt(argc, argv, "r:s:b:B:m:M:e:E:P:p:f:ax"))
    switch (opt)
      {
      case 'r':
        mode = RESEARCH;
        length = atoi(optarg);
        break;
      case 's':
        mode = STATS;
        length = atoi(optarg);
        break;
      case 'b':
        mode = START;
        length = atoi(optarg);
        break;
      case 'B':
        mode = START;
        language = optarg;
        break;
      case 'm':
        mode = MIDDLE;
        length = atoi(optarg);
        break;
      case 'M':
        mode = MIDDLE;
        language = optarg;
        break;
      case 'e':
        mode = END;
        length = atoi(optarg);
        break;
      case 'E':
        mode = END;
        language = optarg;
        break;
      case 'P':
        topOnly = true;
      case 'p':
        mode = SELECTION;
      case 'f':
        language = optarg;
        break;
      case 'a':
        {
          length = sizeof(languages) / sizeof(Language);
          Language selection[length];
          Language* langs = languages;
          if (argc > optind)
            {
              langs = selection;
              for (int i = optind; i < argc; i++)
                for (int k = 0; k < length; k++)
                  if (!strcmp(argv[i], languages[k].name))
                    {
                      langs[i - optind] = languages[k];
                      break;
                    }
              length = argc - optind;
            }
          preparelangs(length, langs);
          wcout.imbue(unicode);
          for (int i = 0; i < length; i++)
            wcout << langs[i].name << ": " << langs[i].uniques << endl;
          return 0;
        }
      case 'x':
        exactLen = true;
      case 0:
        break;
      default:
        cerr << "Invalid invocation" << endl;
        return 1;
      }

  preparelangs(sizeof(languages) / sizeof(Language), languages);

  try
    {
      switch (mode)
        {
        case RESEARCH:
          if (length < 2)
            {
              cerr << "Invalid max sequence length for research" << endl;
              return 1;
            }
          research(length, language, argc, argv);
          break;
        case STATS:
          if (length < 2)
            {
              cerr << "Invalid max sequence length for stat" << endl;
              return 1;
            }
          getstats(length, argc, argv);
          break;
        case SELECTION:
          makeselections(language, topOnly);
          break;
        default:
          Db db(NULL, 0);
          db.set_error_stream(&cerr);
          db.open(NULL, dbFile, NULL, DB_BTREE, DB_CREATE | DB_TRUNCATE, 0);
          if ((mode == ENTIRE) || length)
            {
              if ((mode != ENTIRE) && !exactLen)
                for (int itemLen = 2; itemLen <= length; itemLen++)
                  finduniques(db, mode, itemLen, language, argc, argv);
              else finduniques(db, mode, length, language, argc, argv);
              outputresult(db, mode, language, argc, argv);
            }
          else postprocess(db, mode, language);
          db.close(0);
          break;
        }
    }
  catch(DbException &e)
    {
      cerr << e.what() << endl;
      return 2;
    }
  catch(exception &e)
    {
      cerr << e.what() << endl;
      return 3;
    }

  return 0;
}
