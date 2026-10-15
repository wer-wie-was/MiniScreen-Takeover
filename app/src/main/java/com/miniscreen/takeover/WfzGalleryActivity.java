package com.miniscreen.takeover;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** A gallery contains watchfaces, not additional app profiles. */
public final class WfzGalleryActivity extends Activity {
    private static final int IMPORT=91;
    private Model model;private ProfileStore store;private String profile,query="";private boolean favorites;
    private boolean resumed;
    private TextView status;private Button add;private GridView grid;private Cards adapter;
    private Dialog detail;private PreviewFrame live;
    private String detailId;private ImageView renderedDetail;private TextView previewNote;private int previewMode;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable update=this::refresh;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private String t(int id){return I18n.get(id);}
    private String fmt(int id,Object...args){return String.format(I18n.locale(),t(id),args);}
    @Override protected void attachBaseContext(Context c){super.attachBaseContext(I18n.wrap(c));}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);store=new ProfileStore(this);
        profile=saved!=null?saved.getString("profile",store.active()):getIntent().getStringExtra("profile");if(profile==null)profile=store.active();
        try{store.dir(profile);}catch(Exception e){finish();return;}
        if(saved!=null){query=saved.getString("query","");favorites=saved.getBoolean("favorites");}
        Object retained=getLastNonConfigurationInstance();model=retained instanceof Model&&((Model)retained).language.equals(I18n.locale().toLanguageTag())?(Model)retained:new Model(getApplicationContext());if(retained instanceof Model&&retained!=model)((Model)retained).close();model.listener=update;model.thumbListener=this::thumbnailReady;
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(8),dp(12),0);
        TextView title=new TextView(this);title.setText(t(R.string.gallery_title));title.setTextSize(24);root.addView(title);
        TextView target=new TextView(this);target.setText(fmt(R.string.gallery_target,store.load(profile).name));root.addView(target);
        add=button(root,t(R.string.gallery_import),()->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);try{startActivityForResult(intent,IMPORT);}catch(ActivityNotFoundException e){showError(e);}});
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint(t(R.string.gallery_search));search.setText(query);root.addView(search);
        CheckBox filter=new CheckBox(this);filter.setText(t(R.string.gallery_favorites));filter.setChecked(favorites);root.addView(filter);filter.setOnCheckedChangeListener((v,checked)->{favorites=checked;refresh();});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString();refresh();}public void afterTextChanged(Editable e){}});
        status=new TextView(this);root.addView(status);grid=new GridView(this);grid.setNumColumns(GridView.AUTO_FIT);grid.setColumnWidth(dp(150));grid.setHorizontalSpacing(dp(8));grid.setVerticalSpacing(dp(8));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);adapter=new Cards();grid.setAdapter(adapter);grid.setOnItemClickListener((parent,view,position,id)->open(adapter.items.get(position)));root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        refresh();if(!model.loaded&&!model.busy)model.load();
    }
    private Button button(LinearLayout root,String text,Runnable action){Button button=new Button(this);button.setText(text);button.setOnClickListener(v->action.run());root.addView(button);return button;}
    private void refresh(){if(adapter==null||isDestroyed())return;refreshRenderedDetail();if(model.openMain&&resumed){model.openMain=false;startActivity(new Intent(this,MainActivity.class).setAction(WfzOpenActivity.IMPORTED).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();return;}add.setEnabled(!model.busy);grid.setEnabled(!model.busy);status.setText(model.busy?t(R.string.msg_114):model.report);adapter.items.clear();String find=query.toLowerCase(Locale.ROOT).trim();for(WfzGallery.Entry entry:model.entries)if((!favorites||entry.favorite)&&entry.title.toLowerCase(Locale.ROOT).contains(find))adapter.items.add(entry);adapter.notifyDataSetChanged();if(!model.busy&&adapter.items.isEmpty()&&model.report.isEmpty())status.setText(t(R.string.gallery_empty));}
    private final class Cards extends BaseAdapter {
        final List<WfzGallery.Entry> items=new ArrayList<>();public int getCount(){return items.size();}public Object getItem(int p){return items.get(p);}public long getItemId(int p){return p;}
        public View getView(int position,View old,ViewGroup parent){
            LinearLayout card;ImageView image;TextView label,report;
            if(old==null){
                card=new LinearLayout(WfzGalleryActivity.this);card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(6),dp(6),dp(6),dp(6));card.setBackgroundColor(0xFF25384A);
                image=new ImageView(WfzGalleryActivity.this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                card.addView(image,new LinearLayout.LayoutParams(-1,dp(140)));
                label=new TextView(WfzGalleryActivity.this);label.setTextSize(16);
                int titleHeight=reserveLines(label,2);card.addView(label,new LinearLayout.LayoutParams(-1,titleHeight));
                report=new TextView(WfzGalleryActivity.this);report.setTextSize(11);
                int reportHeight=reserveLines(report,1);card.addView(report,new LinearLayout.LayoutParams(-1,reportHeight));
                // GridView must measure every item to the same height, including
                // its reference item and recycled items after scrolling.
                card.setLayoutParams(new AbsListView.LayoutParams(-1,card.getPaddingTop()+dp(140)+titleHeight+reportHeight+card.getPaddingBottom()));
            }else{
                card=(LinearLayout)old;image=(ImageView)card.getChildAt(0);label=(TextView)card.getChildAt(1);report=(TextView)card.getChildAt(2);
            }
            image.setVisibility(View.VISIBLE);label.setVisibility(View.VISIBLE);report.setVisibility(View.VISIBLE);
            WfzGallery.Entry entry=items.get(position);label.setText((entry.favorite?"★ ":"")+entry.title);image.setContentDescription(entry.title);image.setTag(entry.id);Bitmap cached=model.thumbs.get(entry.id);if(cached==null)image.setImageResource(android.R.drawable.ic_menu_gallery);else image.setImageBitmap(cached);
            // Reports are bounded metadata, not a scene/bitmap decode.
            String summary=WfzImport.summary(entry.folder);int line=summary.indexOf('\n');report.setText(line>=0?summary.substring(line+1):summary);
            if(model.failed.contains(entry.id))report.setText(t(R.string.gallery_render_failed));
            else if(cached==null)model.request(entry,false);
            return card;
        }
    }
    /** Reserve text space using the current system font size, even for short titles. */
    private int reserveLines(TextView text,int lines){
        text.setLines(lines);text.setEllipsize(TextUtils.TruncateAt.END);text.setGravity(Gravity.TOP|Gravity.START);
        text.setIncludeFontPadding(true);
        Paint.FontMetricsInt metrics=text.getPaint().getFontMetricsInt();
        return metrics.bottom-metrics.top+(lines-1)*text.getLineHeight()+text.getCompoundPaddingTop()+text.getCompoundPaddingBottom();
    }
    private void open(WfzGallery.Entry entry){
        if(model.busy)return;
        closeDetail();LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(8),dp(12),dp(8));
        TextView name=new TextView(this);name.setText(entry.title);name.setTextSize(22);box.addView(name);
        TextView note=new TextView(this);note.setText(t(R.string.gallery_rendered_note));box.addView(note);previewNote=note;detailId=entry.id;previewMode=0;
        FrameLayout area=new FrameLayout(this);int edge=Math.min(getResources().getDisplayMetrics().widthPixels-dp(48),getResources().getDisplayMetrics().heightPixels/2);box.addView(area,new LinearLayout.LayoutParams(-1,Math.max(dp(80),edge)));
        ImageView rendered=new ImageView(this);rendered.setScaleType(ImageView.ScaleType.FIT_CENTER);renderedDetail=rendered;
        Bitmap cached=entry.id.equals(model.detailThumbId)&&model.detailThumb!=null?model.detailThumb:model.thumbs.get(entry.id);if(cached==null)rendered.setImageResource(android.R.drawable.ic_menu_gallery);else rendered.setImageBitmap(cached);area.addView(rendered,new FrameLayout.LayoutParams(-1,-1));model.request(entry,true);
        ImageView original=new ImageView(this);original.setScaleType(ImageView.ScaleType.FIT_CENTER);original.setVisibility(View.GONE);area.addView(original,new FrameLayout.LayoutParams(-1,-1));
        boolean[] originalRequested={false};String[] originalNote={t(R.string.gallery_original_note)};
        Spinner mode=new Spinner(this);ArrayAdapter<String> modes=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{t(R.string.gallery_rendered),t(R.string.gallery_live),t(R.string.gallery_supplied)});modes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);mode.setAdapter(modes);box.addView(mode);
        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
            previewMode=position;
            if(position==1){if(live==null){Config config=Config.from(store.load(profile).json());WfzGallery.attach(config,entry.id);live=new PreviewFrame(WfzGalleryActivity.this,WfzGalleryActivity.this,340,340);live.expanded(true);live.guides(false,0);live.surface.apply(config);area.addView(live,new FrameLayout.LayoutParams(edge,edge,Gravity.CENTER));}live.setVisibility(View.VISIBLE);live.surface.start();}
            else if(live!=null){live.surface.stop();live.setVisibility(View.GONE);}
            rendered.setVisibility(position==0?View.VISIBLE:View.GONE);original.setVisibility(position==2?View.VISIBLE:View.GONE);note.setVisibility(position==1?View.GONE:View.VISIBLE);
            note.setText(position==2?originalNote[0]:t(model.failed.contains(entry.id)?R.string.gallery_render_failed:R.string.gallery_rendered_note));
            if(position==2&&!originalRequested[0]&&!model.worker.isShutdown()){
                originalRequested[0]=true;original.setImageResource(android.R.drawable.ic_menu_gallery);
                model.worker.execute(()->{Bitmap decoded=null;try{decoded=WfzGallery.thumbnail(entry,480);}catch(RuntimeException|OutOfMemoryError ignored){}final Bitmap image=decoded;main.post(()->{if(!isDestroyed()&&original.isAttachedToWindow()){if(image!=null)original.setImageBitmap(image);else{originalNote[0]=t(R.string.gallery_no_preview);if(previewMode==2)note.setText(originalNote[0]);}}});});
            }
        }});
        button(box,t(R.string.gallery_use),()->{closeDetail();model.run(()->{WfzGallery.use(model.context,profile,entry.id);model.openMain=true;return "";},null);});
        button(box,t(entry.favorite?R.string.gallery_unfavorite:R.string.gallery_favorite),()->{closeDetail();model.run(()->{WfzGallery.favorite(model.context,entry.id,!entry.favorite);return "";},null);});
        button(box,t(R.string.gallery_delete),()->{
            closeDetail();model.run(()->String.join("\n",WfzGallery.users(model.context,entry.id)),()->{
                if(!model.report.isEmpty()){new AlertDialog.Builder(this).setTitle(t(R.string.gallery_delete)).setMessage(t(R.string.gallery_used)+"\n"+model.report).setPositiveButton(android.R.string.ok,null).show();return;}
                new AlertDialog.Builder(this).setTitle(t(R.string.gallery_delete)).setMessage(t(R.string.gallery_delete_confirm)).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->model.run(()->{WfzGallery.delete(model.context,entry.id);model.thumbs.remove(entry.id);return "";},null)).show();
            });
        });
        TextView details=new TextView(this);details.setText(WfzImport.report(entry.folder));box.addView(details);
        ScrollView scroll=new ScrollView(this);scroll.addView(box);detail=new Dialog(this);detail.setTitle(t(R.string.gallery_title));detail.setContentView(scroll);detail.setOnDismissListener(d->{if(live!=null){live.surface.dispose();live=null;}detailId=null;renderedDetail=null;previewNote=null;model.detailThumb=null;model.detailThumbId=null;detail=null;});detail.show();if(detail.getWindow()!=null)detail.getWindow().setLayout(-1,-2);
    }
    private void refreshRenderedDetail(){
        if(detailId==null||renderedDetail==null)return;
        Bitmap image=detailId.equals(model.detailThumbId)&&model.detailThumb!=null?model.detailThumb:model.thumbs.get(detailId);if(image!=null)renderedDetail.setImageBitmap(image);
        if(previewMode==0&&previewNote!=null)previewNote.setText(t(model.failed.contains(detailId)?R.string.gallery_render_failed:R.string.gallery_rendered_note));
    }
    private void thumbnailReady(String id){
        refreshRenderedDetail();if(grid==null)return;Bitmap bitmap=model.thumbs.get(id);
        for(int i=0;i<grid.getChildCount();i++){
            LinearLayout card=(LinearLayout)grid.getChildAt(i);ImageView image=(ImageView)card.getChildAt(0);
            if(id.equals(image.getTag())){
                if(bitmap!=null)image.setImageBitmap(bitmap);
                if(model.failed.contains(id))((TextView)card.getChildAt(2)).setText(t(R.string.gallery_render_failed));
            }
        }
    }
    private void closeDetail(){if(detail!=null)detail.dismiss();}
    private void showError(Exception e){new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton(android.R.string.ok,null).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=IMPORT||result!=RESULT_OK||data==null)return;List<Uri> files=new ArrayList<>();if(data.getClipData()!=null){if(data.getClipData().getItemCount()>32){showError(new java.io.IOException(t(R.string.gallery_batch_limit)));return;}for(int i=0;i<data.getClipData().getItemCount();i++)files.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)files.add(data.getData());if(!files.isEmpty())model.importFiles(files);}
    @Override public Object onRetainNonConfigurationInstance(){return model;}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("profile",profile);out.putString("query",query);out.putBoolean("favorites",favorites);}
    @Override protected void onResume(){super.onResume();resumed=true;if(!model.language.equals(I18n.locale().toLanguageTag())){recreate();return;}refresh();}
    @Override protected void onPause(){resumed=false;closeDetail();super.onPause();}
    @Override protected void onDestroy(){closeDetail();if(model!=null&&model.listener==update){model.listener=null;model.thumbListener=null;}if(model!=null&&!isChangingConfigurations())model.close();main.removeCallbacksAndMessages(null);super.onDestroy();}
    private interface Job{String run()throws Exception;}
    private static final class Model {
        final String language=I18n.locale().toLanguageTag();
        final LruCache<String,Bitmap> thumbs=new LruCache<String,Bitmap>(4*1024*1024){@Override protected int sizeOf(String key,Bitmap image){return image.getByteCount();}};
        final Set<String> pending=new HashSet<>(),failed=new HashSet<>();
        final ArrayDeque<WfzGallery.Entry> queue=new ArrayDeque<>();boolean thumbnailBusy,closed;
        String detailThumbId;Bitmap detailThumb;
        java.util.function.Consumer<String> thumbListener;
        final Context context;final ExecutorService worker=Executors.newSingleThreadExecutor();final Handler main=new Handler(Looper.getMainLooper());List<WfzGallery.Entry> entries=new ArrayList<>();boolean loaded,busy,openMain;String report="";Runnable listener;
        Model(Context c){context=c;}
        void close(){closed=true;queue.clear();pending.clear();thumbs.evictAll();detailThumb=null;detailThumbId=null;worker.shutdown();}
        void request(WfzGallery.Entry entry,boolean priority){
            if(closed||worker.isShutdown()||failed.contains(entry.id))return;
            if(priority){if(!entry.id.equals(detailThumbId)){detailThumb=null;detailThumbId=entry.id;}if(detailThumb!=null)return;}
            else if(thumbs.get(entry.id)!=null)return;
            if(pending.contains(entry.id)){
                if(priority&&queue.removeIf(item->item.id.equals(entry.id)))queue.addFirst(entry);
                return;
            }
            if(queue.size()>=64)pending.remove(queue.removeFirst().id);
            pending.add(entry.id);if(priority)queue.addFirst(entry);else queue.addLast(entry);pump();
        }
        private void pump(){
            if(closed||thumbnailBusy||queue.isEmpty()||worker.isShutdown())return;
            WfzGallery.Entry entry=queue.removeFirst();thumbnailBusy=true;
            worker.execute(()->{
                try{
                    Bitmap cached=WfzThumbnails.read(context,entry.id,language);
                    if(cached!=null){main.post(()->complete(entry,cached));return;}
                    WfzScene scene;
                    synchronized(WfzGallery.class){if(!entry.folder.isDirectory())throw new java.io.IOException("Watchface removed");scene=WfzScene.load(entry.folder,new Config());}
                    main.post(()->render(entry,scene));
                }catch(Exception|OutOfMemoryError e){main.post(()->complete(entry,null));}
            });
        }
        private void render(WfzGallery.Entry entry,WfzScene scene){
            if(closed){complete(entry,null);return;}
            if(!language.equals(I18n.locale().toLanguageTag())){complete(entry,null);return;}
            try{
                Bitmap image=WfzView.snapshot(context,scene,WfzThumbnails.EDGE);
                worker.execute(()->{
                    synchronized(WfzGallery.class){
                        if(!entry.folder.isDirectory()){main.post(()->complete(entry,null));return;}
                        // A full cache must not prevent displaying a successfully rendered frame.
                        if(language.equals(I18n.locale().toLanguageTag()))try{WfzThumbnails.write(context,entry.id,image,language);}catch(java.io.IOException|RuntimeException ignored){}
                    }
                    main.post(()->complete(entry,image));
                });
            }catch(Exception|OutOfMemoryError e){complete(entry,null);}
        }
        private void complete(WfzGallery.Entry entry,Bitmap image){
            pending.remove(entry.id);thumbnailBusy=false;
            if(closed)return;
            if(image==null)failed.add(entry.id);else{
                // Grid tiles stay small so a screenful cannot evict itself and
                // continually reload. Keep just one full-size dialog snapshot.
                thumbs.put(entry.id,Bitmap.createScaledBitmap(image,180,180,true));
                if(entry.id.equals(detailThumbId))detailThumb=image;
            }
            if(thumbListener!=null)thumbListener.accept(entry.id);pump();
        }
        void changed(){if(listener!=null)listener.run();}
        void load(){run(()->{int failures=WfzGallery.migrate(context);return failures==0?"":String.format(I18n.locale(),I18n.get(R.string.gallery_migration_failed),failures);},null);}
        void run(Job job,Runnable done){if(busy||worker.isShutdown())return;busy=true;final Runnable owner=listener;changed();worker.execute(()->{String message;boolean success;try{message=job.run();success=true;}catch(Exception e){message=e.getMessage();success=false;}List<WfzGallery.Entry> next=WfzGallery.list(context);final String text=message==null?"":message;final boolean ok=success;main.post(()->{entries=next;report=text;loaded=true;busy=false;failed.clear();thumbs.evictAll();changed();if(ok&&done!=null&&listener==owner&&listener!=null)done.run();});});}
        void importFiles(List<Uri> files){run(()->{int added=0,duplicates=0;List<String> errors=new ArrayList<>();for(Uri uri:files)try{WfzGallery.Result result=WfzGallery.importFile(context,uri);if(result.duplicate)duplicates++;else added++;}catch(Exception e){errors.add(WfzImport.displayName(context,uri)+": "+e.getMessage());}String summary=String.format(I18n.locale(),I18n.get(R.string.gallery_import_result),added,duplicates,errors.size());return errors.isEmpty()?summary:summary+"\n"+String.join("\n",errors);},null);}
    }
}
