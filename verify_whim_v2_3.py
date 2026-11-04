from pathlib import Path
import sys
root=Path(sys.argv[1])
html=root/'app/src/main/assets/index.html'
java=root/'app/src/main/java/com/infinitycyber/whim/MainActivity.java'
manifest=root/'app/src/main/AndroidManifest.xml'
required={
 'HTML':html.is_file(), 'Android activity':java.is_file(), 'manifest':manifest.is_file(),
}
for k,v in required.items(): print(('PASS' if v else 'FAIL')+': '+k)
if not all(required.values()): raise SystemExit(1)
s=html.read_text(encoding='utf-8'); j=java.read_text(encoding='utf-8'); m=manifest.read_text(encoding='utf-8')
checks={
 'Excel central-directory reader':'central directory' in s,
 'Excel restore resolves workbook relationships':'workbook.xml.rels' in s,
 'Excel export preserves movement detail':'Inbound Boxes' in s and 'Cylinder' in s and 'Meters' in s,
 'Excel export preserves material balances':'Previous Balance' in s and 'Planned Inbound' in s,
 'OCR lazy loader is called':'await window.ensureWHIMOCR()' in s,
 'JSON backup filename':'WHIM_Inventory_Backup.json' in s,
 'Android Storage Access Framework':'ACTION_CREATE_DOCUMENT' in j,
 'Android raw DEFLATE fallback':'inflateRawBase64' in j,
 'Blob/data download bridge':'JSONObject.quote(url)' in j,
 'Chrome/default browser redirects':'HTTPS links and redirects open in Chrome/default browser.' in j,
 'Camera permission not duplicated':m.count('android.permission.CAMERA')==1,
}
for k,v in checks.items(): print(('PASS' if v else 'FAIL')+': '+k)
if not all(checks.values()): raise SystemExit(1)
