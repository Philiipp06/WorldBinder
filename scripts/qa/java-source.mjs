// Small lexer for language auditing, not a Java parser. Comments and character literals are skipped;
// text blocks remain single tokens, so prose inside comments cannot hide real UI literals.
export function javaStrings(source) {
  const result = [];
  const tokens = /\/\*[\s\S]*?\*\/|\/\/[^\r\n]*|"""[\s\S]*?"""|"(?:\\[\s\S]|[^"\\])*"|'(?:\\[\s\S]|[^'\\])*'/g;
  for (const match of source.matchAll(tokens)) {
    if (!match[0].startsWith('"')) continue;
    const raw = match[0];
    const block = raw.startsWith('"""');
    const value = (block ? raw.slice(3, -3) : raw.slice(1, -1))
      .replace(/\\u([\da-f]{4})/gi, (_, digits) => String.fromCharCode(parseInt(digits, 16)))
      .replace(/\\([bnfrt"'\\])/g, (_, escaped) => ({b:'\b',n:'\n',f:'\f',r:'\r',t:'\t'}[escaped] ?? escaped));
    const lineStart = source.lastIndexOf('\n', match.index) + 1;
    const lineEnd = source.indexOf('\n', match.index);
    result.push({value, raw, start:match.index, end:match.index + raw.length,
      line:source.slice(0, match.index).split('\n').length,
      source:source.slice(lineStart, lineEnd < 0 ? source.length : lineEnd)});
  }
  return result;
}

export function argumentsAt(source, start) {
  let depth=1,quote=null,escape=false,begin=start,result=[];
  for(let i=start;i<source.length;i++) {
    const c=source[i];
    if(quote){if(escape)escape=false;else if(c==='\\')escape=true;else if(c===quote)quote=null;continue;}
    if(c==='"'||c==="'"){quote=c;continue;}
    if('([{'.includes(c))depth++;
    else if(')]}'.includes(c)){if(--depth===0){result.push(source.slice(begin,i).trim());return result;}}
    else if(c===','&&depth===1){result.push(source.slice(begin,i).trim());begin=i+1;}
  }
  return [];
}

export function needsTranslation(value) {
  // Words in sentences, including short fragments such as "s ago", and individual natural-language
  // labels. IDs, paths, format strings, numeric units and machine field names are checked separately
  // at UI entry points; they are not automatically treated as prose.
  return /^[A-Z][a-z]{2,}$/.test(value) || /\p{L}[^\r\n]*\s+[^\r\n]*\p{L}/u.test(value)
    || /[äöüß]/i.test(value);
}

export function enumNames(source, name) {
  const code = source.replace(/\/\*[\s\S]*?\*\/|\/\/[^\r\n]*|"(?:\\[\s\S]|[^"\\])*"|'(?:\\[\s\S]|[^'\\])*'/g, value => value.replace(/[^\r\n]/g, ' '));
  const declaration = new RegExp('\\benum\\s+'+name+'\\s*\\{').exec(code);
  if (!declaration) throw Error('Missing enum '+name);
  let depth=0, next=true, result=[];
  for(let i=declaration.index+declaration[0].length;i<code.length;i++) {
    const c=code[i];
    if(depth===0 && (c===';'||c==='}'))return result;
    if(next && /[A-Za-z_]/.test(c)) {
      const word=/^[\w]+/.exec(code.slice(i))[0];
      result.push(word.toLowerCase());i+=word.length-1;next=false;continue;
    }
    if('({['.includes(c))depth++;
    else if(')}]'.includes(c))depth--;
    else if(c===','&&depth===0)next=true;
  }
  throw Error('Unterminated enum '+name);
}
