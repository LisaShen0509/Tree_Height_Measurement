# -*- coding:UTF-8 -*-
import flask
import werkzeug
import time
import os
import shutil
import cv2
import matplotlib.pyplot as plt
import matplotlib
import numpy as np
import random
from PIL import Image
import pandas as pd
import open3d as o3d
import torch
import argparse
from glob import glob
import utils
import argparse
import torch.backends.cudnn as cudnn
import yaml
import archs
import copy
import image_util
import json

app = flask.Flask(__name__)

global timestamp
timestamp=""
global depth_model
global seg_model
global transform
global device
global config
global pred_depth_image_path
global blend_image_path
global cloud_path
global original_mask_path
global threshold_mask_path
global fx
global fy
global cx
global cy
# global final_mask_path
model_type='MiDaS' # 'DPT_Hybrid', 'MiDaS'
input_path='input'
output_path=model_type+"_output"
#绝对深度图尺寸
H = 120 
W = 160

# scale = 160/4608 #RGB除以手机相机实际尺寸
scale = 160/1440 #RGB除以手机相机实际尺寸

#图像分割网络权重文件
# model_path='resized_tree_dataset_512_DepthAttentionUNet_woDS2023-03-14_08_12_27.437440'
model_path='tree512_DepthAttentionUNet_woDS2023-04-07_06_47_57.138338'
colors = [(0, 0, 0), (255, 255, 255)] #mask的颜色，背景黑，树白

#相对深度拟合绝对深度的级数
level=2

#填充不同轮廓
colors = [(128, 0, 0), (0, 128, 0), (128, 128, 0), (0, 0, 128), (128, 0, 128), (0, 128, 128), 
            (128, 128, 128), (64, 0, 0), (192, 0, 0), (64, 128, 0), (192, 128, 0), (64, 0, 128), (192, 0, 128), 
            (64, 128, 128), (192, 128, 128), (0, 64, 0), (128, 64, 0), (0, 192, 0), (128, 192, 0), (0, 64, 128), (128, 64, 12)]

@app.before_first_request
def startup():
    torch.cuda.empty_cache()
    cudnn.enabled = True
    cudnn.benchmark = True
    global device
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Device: {device}")
    global depth_model
    depth_model = torch.hub.load("intel-isl/MiDaS",model_type)
    depth_model.eval()
    depth_model.to(device)

    transforms = torch.hub.load("intel-isl/MiDaS", "transforms")

    global transform
    if model_type in ['DPT_Large', 'DPT_Hybrid', 'MiDaS']:
        transform = transforms.dpt_transform
    else:
        transform = transforms.small_transform

    global config
    with open('models/'+model_path+'/config.yml', 'r') as f:
        config = yaml.load(f, Loader=yaml.FullLoader)

    #输出网络参数
    # print('-'*20)
    # for key in config.keys():
    #     print('%s: %s' % (key, str(config[key])))
    # print('-'*20)

    # create model
    print("=> creating model %s" % config['arch'])
    #UNet and UNet++
    # model = archs.__dict__[config['arch']](config['num_classes'],
    #             config['input_channels'],
    #             config['deep_supervision'])

    #AttUNet
    # model = archs.__dict__[config['arch']](config['input_channels'],1)

    #DepthAttUNet
    global seg_model
    seg_model = archs.__dict__[config['arch']](config['input_channels'],1,1)

    seg_model.load_state_dict(torch.load('models/'+model_path+'/model.pth'))
    seg_model.cuda()
    print("模型已加载")

@app.route('/getData', methods = ['GET', 'POST'])
def handle_request():
    files_ids = list(flask.request.files)
    print("\n收到的文件数 : ", len(files_ids))
    global fx
    global fy
    global cx
    global cy
    file_num = 1
    for file_id in files_ids:
        file = flask.request.files[file_id]
        if file_num<5:
            print("\n保存文件 ", str(file_num), "/", len(files_ids))
            filename = werkzeug.utils.secure_filename(file.filename)
            print("文件名: " + file.filename)
            file.save(os.path.join(input_path,filename))
            form=os.path.splitext(filename)[1]
            if form=='.jpg':
                global timestamp
                timestamp=os.path.splitext(filename)[0]
        elif file_num==5:
            fx=float(file.filename)
        elif file_num==6:
            fy=float(file.filename)
        elif file_num==7:
            cx=float(file.filename)
        else:
            cy=float(file.filename)
        file_num = file_num + 1
    # print(fx," ",fy," ",cx," ",cy)
    fx*=scale
    fy*=scale
    cx*=scale
    cy*=scale

    height=Calculate()
    print(height)
    global pred_depth_image_path
    rel_depth=image_util.image_to_base64(pred_depth_image_path)
    global blend_image_path
    mask=image_util.image_to_base64(blend_image_path)
    response_data=[{
        "heights":height,
        "rel_depth":rel_depth,
        "mask":mask,
        # "tree_cloud":{
        #     "points":np.asarray(radius_cloud.points).tolist(),
        #     "colors":np.asarray(radius_cloud.colors).tolist(),
        # }
    }]
    json_str = json.dumps(response_data)
    f2 = open('new_json.json', 'w')
    f2.write(json_str)
    f2.close()

    # json_str=''
    # # 打开文件,r是读取,encoding是指定编码格式
    # with open('new_json.json','r',encoding = 'utf-8') as fp:
    #     # load()函数将fp(一个支持.read()的文件类对象，包含一个JSON文档)反序列化为一个Python对象
    #     data = json.load(fp)
    #     json_str=json.dumps(data)
    # fp.close()
    # RemoveFiles()

    return json_str


@app.route('/get_point_cloud')
def get_point_cloud():
    # 从文件加载PLY文件
    global cloud_path

    # 将PLY文件发送到客户端
    return flask.send_file(cloud_path, as_attachment=True)


def Calculate():
    global pred_depth_image_path
    global threshold_mask_path
    global blend_image_path
    global cloud_path
    global fx
    global fy
    global cx
    global cy
    global transform
    global device
    global depth_model
    global original_mask_path
   
    #------------生成相对深度图-------------------------
    img_name=os.path.join(input_path,timestamp+'.jpg')
    print("开始生成相对深度图")
    # input 
    img = np.array(Image.open(img_name))
    sample = transform(img).to(device)   
    with torch.no_grad():
        pred_depth_image = depth_model.forward(sample)
        pred_depth_image = (
            torch.nn.functional.interpolate(
                pred_depth_image.unsqueeze(1),
                size=img.shape[:2],
                mode="bicubic",
                align_corners=False,
            )
            .squeeze()
            .cpu()
            .numpy()
        )

    # save output
    filename = os.path.join(
        output_path, os.path.splitext(os.path.basename(img_name))[0]
    )
    # global pred_depth_image_path
    pred_depth_image_path=filename+'.png'
    utils.write_depth(filename, pred_depth_image, bits=2)
    print(f"生成成功，文件名: {filename}")
    img=cv2.imread(pred_depth_image_path,0)
    cv2.imwrite(pred_depth_image_path,img)

    torch.cuda.empty_cache()
    

    #-----------图像分割-----------------------------------
    
    image = Image.open(os.path.join(input_path,timestamp+'.jpg'))
    depth = cv2.imread(os.path.join(output_path,timestamp+'.png'),cv2.IMREAD_GRAYSCALE)
    depth = Image.fromarray(depth)
    image = image.convert('RGB')
    old_img = copy.deepcopy(image)#备份用于绘图
    orininal_h = np.array(image).shape[0]
    orininal_w = np.array(image).shape[1]

    #   进行不失真的resize，添加灰条，进行图像归一化
    global config
    image, nw, nh = image_util.letterbox_image(image,(config['input_w'],config['input_h']))
    image=np.asarray(image)[..., None]
    image = image.astype('float32') / 255
    images = image.transpose(3,2,0,1)#交换坐标轴
    images = torch.from_numpy(images).type(torch.FloatTensor)

    depth, nw, nh = image_util.letterbox_depth(depth,(config['input_w'],config['input_h']))
    # depth.show()
    depth=np.asarray(depth)[..., None]
    depth=depth[...,None]
    depth = depth.astype('float32') / 255
    depth = depth.transpose(3,2, 0, 1)
    depth = torch.from_numpy(depth).type(torch.FloatTensor)

    with torch.no_grad():
        images = images.cuda()
        depth = depth.cuda()  
        global seg_model
        output = seg_model(images,depth)#input shape应该是[1, 3, H, W]  [4, 3, 256, 256] batchsize channels height width
        output = torch.sigmoid(output).cpu().numpy()
        # print(output.shape)
        seg_img=(output[0,0,int((config['input_h']-nh)//2):int((config['input_h']-nh)//2+nh), int((config['input_w']-nw)//2):int((config['input_w']-nw)//2+nw)]* 255).astype('uint8')
    
    torch.cuda.empty_cache()
    seg_img = Image.fromarray(seg_img).resize((orininal_w,orininal_h))
    original_mask_path=timestamp+"_original_mask.png"
    seg_img.save(original_mask_path)

    # 1.全局阈值法
    ret, threshold_mask = cv2.threshold(src=np.asarray(seg_img),# 要二值化的图片
        thresh=125,               # 全局阈值
        maxval=255,               # 大于全局阈值后设定的值
        type=cv2.THRESH_BINARY)   # 设定的二值化类型，THRESH_BINARY：表示小于阈值置0，大于阈值置填充色

    kernel = np.ones((3, 3), np.uint8)
    threshold_mask = cv2.morphologyEx(threshold_mask, cv2.MORPH_OPEN, kernel, iterations=4)#开运算，先腐蚀后膨胀
    # global threshold_mask_path
    threshold_mask_path=timestamp+'_threshold_mask.png'
    cv2.imwrite(threshold_mask_path,threshold_mask)

    depthData = np.fromfile(os.path.join(input_path,timestamp+'_depthdata.txt'), dtype = np.uint16) 
    depthData=depthData.reshape(H,W)
    depthData = cv2.rotate(depthData, cv2.ROTATE_90_CLOCKWISE)

    cameraImage=cv2.imread(os.path.join(input_path,timestamp+'.jpg'), cv2.COLOR_BGR2RGB)
    img=cv2.resize(cameraImage,(H,W))

    rawdepthData = np.fromfile(os.path.join(input_path,timestamp+'_rawdepthdata.txt'), dtype = np.uint16) 
    rawdepthData=rawdepthData.reshape(H,W)
    rawdepthData = cv2.rotate(rawdepthData, cv2.ROTATE_90_CLOCKWISE)

    ConfidenceData = np.fromfile(os.path.join(input_path,timestamp+'_confidencedata.txt'), dtype = np.uint8) 
    ConfidenceData=ConfidenceData.reshape(H,W)
    ConfidenceData = cv2.rotate(ConfidenceData, cv2.ROTATE_90_CLOCKWISE)

    newDepthData=depthData.copy()
    cnt=0
    for i in range(W):
        for j in range(H):
            if rawdepthData[i,j]>0 and ConfidenceData[i,j]>155:
                cnt+=1
                newDepthData[i,j]=rawdepthData[i,j]
    print('替换了',cnt,'个像素')

    pred_depth=cv2.imread(os.path.join(output_path,timestamp+'.png'), cv2.IMREAD_GRAYSCALE)
    pred_depth=cv2.resize(pred_depth,(H,W))#120 160

    pred_depth_aligned = image_util.Rel2Abs(pred_depth,newDepthData,level)

    depth=pred_depth_aligned.astype('uint16')
    # pred_cloud=image_util.CreatePointCloud(img,depth,scale,1000,model_type+'_'+timestamp+'_Depth.ply')
    pred_cloud=image_util.CreatePointCloud(img,depth,fx,fy,cx,cy,scale,1000,model_type+'_'+timestamp+'_Depth.ply')

    selected_heights=dict()
    contours,hierarchy = cv2.findContours(threshold_mask,cv2.RETR_TREE, cv2.CHAIN_APPROX_NONE)
    total_index=[]
    line_fit_trunk=[]
    elli_fit_crown_x=[]
    elli_fit_crown_y=[]
    contour_list=[]
    for i in range(len(contours)):
        x, y, w, h = cv2.boundingRect(contours[i])
        if y> threshold_mask.shape[1]/2 or y+h<threshold_mask.shape[1]*5/6:
            contour_list.append(contours[i])
            continue
        area=cv2.contourArea(contours[i])
        # print("面积=",area)
        if area>0:
            print("-----------计算第",i,"个轮廓-----------------")
            local_mask=threshold_mask.copy()
            temp_mask=local_mask.copy()
            for j in range(len(contours)):
                if j!=i:
                    cv2.fillPoly(local_mask, [contours[j]], 0)
            if local_mask.max()>0:
                # temp_mask=local_mask.copy()
                # #提取mask轮廓
                # # local_contours,hierarchy = cv2.findContours(local_mask,cv2.RETR_TREE, cv2.CHAIN_APPROX_NONE)
                # y_size=np.shape(local_mask)[0]#高
                # # print("y_size=",y_size)
                # x_size=np.shape(local_mask)[1]#宽
                # # print("x_size=",x_size)
                # x,y,w,h=cv2.boundingRect(contours[i]) #提取外接矩形
                # # print("x轴范围:(",x,",",(x+w),")")
                # # print("y轴范围:(",y,",",(y+h),")")
                # length=0
                # max_width_diff=0
                # max_width_idx=y+h
                # #分割树冠和树干部分
                # for row in range(y+h, y, -1):
                #     if row>=y_size:
                #         row-=1
                #     now_len=0
                #     x1=0
                #     x2=0
                #     for col in range(x,x+w):
                #         if(local_mask[row][col] == 255):
                #             x1=col
                #             break
                #     for col in range(x+w,x1,-1):
                #         if col>=x_size:
                #             col-=1
                #         if(local_mask[row][col] == 255):
                #             x2=col
                #             break
                #     if x1==0 or x2==0:
                #         continue
                #     # print("x1=",x1," x2=",x2)
                #     now_len=x2-x1
                #     if now_len>20 and length != 0:
                #         width_diff=now_len-length
                #         if width_diff>max_width_diff:
                #             max_width_diff=width_diff
                #             max_width_idx=row
                #         if max_width_diff>5:
                #             max_width_idx+=2
                #             break
                #     if now_len>20:
                #         length=now_len
                # # print("分界处:",max_width_idx)
                # #树干部分mask
                # trunkmask=local_mask.copy()
                # for row in range(y,max_width_idx):
                #     for col in range(x,x+w):
                #         if trunkmask[row][col]==255:
                #             trunkmask[row][col]=0
                # trunkmask=image_util.find_max_region(trunkmask)
                # #提取树干轮廓
                # trunk_contours,hierarchy = cv2.findContours(trunkmask,cv2.RETR_TREE, cv2.CHAIN_APPROX_NONE)
                # if len(trunk_contours)>0:
                #     #树冠部分mask
                #     crownmask=local_mask.copy()
                #     for row in range(max_width_idx,y+h):
                #         for col in range(x,x+w):
                #             if crownmask[row][col]==255:
                #                 crownmask[row][col]=0
                #     #提取树冠轮廓
                #     crown_contours,hierarchy = cv2.findContours(crownmask,cv2.RETR_TREE, cv2.CHAIN_APPROX_NONE)
                #     #提取树冠的外接矩形
                #     c_x,c_y,c_w,c_h=cv2.boundingRect(crown_contours[0]) 
                #     # print("x轴范围:(",c_x,",",(c_x+c_w),")")
                #     # print("y轴范围:(",c_y,",",(c_y+c_h),")")
                #     #对树干进行椭圆拟合，记录椭圆中心坐标rotatepoint和椭圆旋转的角度rotateangel;
                #     ellip = cv2.fitEllipse(trunk_contours[0])  #椭圆拟合,减少噪声的影响  //contours:轮廓点集合
                #     rotatepoint = ellip[0]  #(x, y)
                #     intpoint=(int(round(rotatepoint[0],0)),int(round(rotatepoint[1],0))) #取整
                #     rotateangel = 180-ellip[2] #angle表示短轴与x轴的夹角 顺时针旋转为正
                #     if rotateangel>90:
                #         rotateangel-=180
                #     #以rotatepoint为旋转中心将树干逆时针旋转rotateangel度，记新轮廓为rota
                #     rota = image_util.rotate_contour(contours[i], rotatepoint, rotateangel)#轮廓旋转函数
                #     #判断树冠是否被遮挡，如果是，还要判断露出的哪部分树冠
                #     blackbg = np.zeros((1440, 1080, 3), dtype=np.uint8)
                #     cv2.fillPoly(blackbg, [rota], (255,255,255))
                #     isObscured=True
                #     thresh=(y+max_width_idx)//2
                #     thresh=(y+thresh)//2
                #     print("y=",y,",thresh=",thresh)
                #     for row in range(y,thresh):
                #         if blackbg[row,intpoint[0]].max()==255:
                #             print('树冠最高点未被遮挡')
                #             isObscured=False
                #             break
                #     isFinish=False
                #     isRightShow=False
                #     if isObscured==True:
                #         print('树冠最高点被遮挡')
                #         for row in range(c_y,thresh):
                #             for col in range(c_x,c_x+c_w):
                #                 if blackbg[row,col].max()==255:
                #                     # print('找到可观测最高点')
                #                     if col<intpoint[0]:
                #                         print('露出的是左侧树冠')
                #                     else:
                #                         print('露出的是右侧树冠')
                #                         isRightShow=True
                #                     isFinish=True
                #                     break
                #             if isFinish==True:
                #                 break
                    # #如果被遮挡，需要拟合估计被遮部分
                    # if isObscured:
                    #     #把mask转回来
                    #     rota_reverse = image_util.rotate_contour(rota, rotatepoint, -rotateangel)
                    #     blackbg = np.zeros((1440, 1080, 3), dtype=np.uint8)
                    #     cv2.fillPoly(blackbg, [rota_reverse], (255,255,255))
                    #     line_point=np.array([[[intpoint[0],y+h+1]], [[intpoint[0],y-50]]])
                    #     #拟合树干的直线也要转
                    #     line_recover=image_util.rotate_contour(line_point, rotatepoint, -rotateangel)
                    #     line_recover=line_recover.reshape(2, 2)
                    #     for coord in line_recover:
                    #         line_fit_trunk.append(coord)
                    #     #找到补充的树冠要画到哪里
                    #     split_x=0
                    #     if line_recover[0,1]<line_recover[1,1]:
                    #         split_x=line_recover[0,0]
                    #     else:
                    #         split_x=line_recover[1,0]
                    #     #提取部分树冠去拟合
                    #     points_x=[]
                    #     points_y=[]
                    #     isFinish=False
                    #     if isRightShow==True:  #露出的是右边树冠，从左往右提取树冠轮廓
                    #         max_col=0
                    #         for row in range(c_y,c_y+c_h):
                    #             if row>=y_size:
                    #                 row-=1
                    #             for col in range(c_x+c_w,c_x,-1):
                    #                 if col>=x_size:
                    #                     col-=1
                    #                 if crownmask[row,col]==255:
                    #                     if col>=max_col:
                    #                         points_x.append(col)
                    #                         points_y.append(row)
                    #                         max_col=col
                    #                     else:
                    #                         isFinish=True
                    #                     break
                    #             if isFinish==True or len(points_x)==20:
                    #                 break
                    #     else:       #露出的是左边树冠，从右往左提取树冠轮廓
                    #         min_col=c_x+c_w+1
                    #         for row in range(c_y,c_y+c_h):
                    #             for col in range(c_x,c_x+c_w):
                    #                 if crownmask[row,col]==255:
                    #                     if col<=min_col:
                    #                         points_x.append(col)
                    #                         points_y.append(row)
                    #                         min_col=col
                    #                     else:
                    #                         isFinish=True
                    #                     break
                    #             if isFinish==True or len(points_x)==20:
                    #                 break
                    #     #得到拟合函数p(x)
                    #     p = np.poly1d(np.polyfit(points_x,points_y, 2))
                    #     #拟合出的树冠部分的坐标
                    #     elli_x=[]
                    #     elli_y=[]
                    #     if isRightShow:
                    #         for n in range(split_x-10,np.asarray(points_x).max()):
                    #             elli_x.append(n)
                    #         for n in elli_x:
                    #             elli_y.append(int(round(p(n),0)))
                    #     else:
                    #         for n in range(np.asarray(points_x).min(),split_x+10):
                    #             elli_x.append(n)
                    #         for n in elli_x:
                    #             elli_y.append(int(round(p(n),0)))
                    #     elli_fit_crown_x.append(elli_x)
                    #     elli_fit_crown_y.append(elli_y)
                    #     #将拟合出的树冠画进去(白色实心)
                    #     blackbg = np.zeros((1440, 1080), dtype=np.uint8)
                    #     cv2.fillPoly(blackbg, [rota_reverse], 255)
                    #     for n in range(len(elli_x)):
                    #         if elli_y[n]>=y_size:
                    #             elli_y[n]=y_size-1
                    #         if elli_y[n]<0:
                    #             elli_y[n]=0
                    #         if elli_x[n]>=x_size:
                    #             elli_x[n]=x_size-1
                    #         if elli_x[n]<0:
                    #             elli_x[n]=0
                    #         blackbg[elli_y[n],elli_x[n]]=255
                    #         # print(elli_y[n],',',elli_x[n])
                    #         for row in range(elli_y[n]+1,c_y+c_h):
                    #             if blackbg[row,elli_x[n]]==255:
                    #                 # print('遇到白色像素',row,',',elli_x[i])
                    #                 break
                    #             blackbg[row,elli_x[n]]=255
                    #     local_mask=blackbg
                    #     #更新最后要画的mask
                    #     new_contours,hierarchy = cv2.findContours(local_mask,cv2.RETR_TREE, cv2.CHAIN_APPROX_NONE)
                    #     contour_list.append(new_contours[0])
                    # else:
                        # contour_list.append(contours[i])
                # else:
                #     contour_list.append(contours[i])
                contour_list.append(contours[i])
                resize_mask=Image.fromarray(local_mask).resize((H,W)) #宽度=H=120
                mask_for_seg=np.asarray(resize_mask).flatten()
                index=[]
                for k in range(len(mask_for_seg)):
                    if mask_for_seg[k]==255:
                        index.append(k)
        
                if len(index)>0:
                    tree_cloud=image_util.display_inlier_outlier(pred_cloud, index)
                    cl, ind = tree_cloud.remove_radius_outlier(nb_points=10, radius=1.5)#nb_points：球体中最少点的数量 radius球的半径
                    radius_cloud = tree_cloud.select_by_index(ind)
                    
                    aabb_box_length=image_util.aabb(radius_cloud)
                    height=aabb_box_length[1]
                    height=round(height,2)
                    print("height=",height)
                    # print('Y轴长度:',aabb_box_length[1])
                    # print('Y轴长度的的一半:',aabb_box_length[1]/2.0)
                    if height>1.5:
                        selected_heights[i]=height
                        # if isObscured: #如果当前树被遮挡，mask被修改过，要还原成原来的mask
                        #     resize_mask=Image.fromarray(temp_mask).resize((H,W)) #宽度=H=120
                        #     mask_for_seg=np.asarray(resize_mask).flatten()
                        #     index=[]
                        #     for k in range(len(mask_for_seg)):
                        #         if mask_for_seg[k]==255:
                        #             index.append(k)
                        for idx in index:
                            total_index.append(idx)
            else:
                contour_list.append(contours[i])
        else:
            contour_list.append(contours[i])
    threshold_mask=Image.fromarray(threshold_mask)
    GRB_mask=threshold_mask.convert('RGB')
    new_mask = np.asarray(GRB_mask.copy())
    new_num=0
    for k in range(len(contours)):
        if k not in selected_heights.keys():  
            cv2.fillPoly(new_mask, [contours[k]], (0,0,0))
        else:
            cv2.fillPoly(new_mask, [contours[k]], colors[new_num])
            new_num+=1

    print("len(contours)=",len(contours))
    print("len(contour_list)=",len(contour_list))
    new_num=0
    for k in range(len(contours)):
        if k in selected_heights.keys():
            if k<len(contour_list):
                # 找到边界坐标
                x, y, w, h = cv2.boundingRect(contour_list[k])  # 计算点集最外面的矩形边界
                #  print(x, y, w, h)
                if not (x == 0 and y == 0 and w == new_mask.shape[1] and h == new_mask.shape[0]):
                    # 左上角坐标和右下角坐标
                    cv2.rectangle(new_mask, (x, y), (x + w, y + h), colors[new_num], 3)
                    tmp_h=selected_heights[k]
                    selected_heights.pop(k)
                    selected_heights[new_num]=tmp_h
                    if y<=50:
                        cv2.putText(new_mask, "tree"+str(new_num)+" "+str(tmp_h)+"m",(x,y+h+42), cv2.FONT_HERSHEY_SIMPLEX, 2.2, (255,255,255), 4)
                    else:
                        # 图片img,“文本内容”,(左下角坐标),字体,字体大小,(颜色)，线条粗细，线条类型
                        cv2.putText(new_mask, "tree"+str(new_num)+" "+str(tmp_h)+"m",(x,y-20), cv2.FONT_HERSHEY_SIMPLEX, 2.2, (255,255,255), 4)
                    new_num+=1
    #如果有树最高点被遮挡，画出拟合树干的直线和拟合树冠的曲线
    # if len(line_fit_trunk)>0:
    #     idx=0
    #     while idx<len(line_fit_trunk)-1:
    #         cv2.line(new_mask,(line_fit_trunk[idx][0],line_fit_trunk[idx][1]),(line_fit_trunk[idx+1][0],line_fit_trunk[idx+1][1]), (255,255,255), 2)
    #         idx+=2
    # if len(elli_fit_crown_x)>0:
    #     color = (255, 255, 255)
    #     radius = 2
    #     thickness = -1  # -1 表示填充整个圆
    #     for i in range(len(elli_fit_crown_x)):
    #         elli_x=elli_fit_crown_x[i]
    #         elli_y=elli_fit_crown_y[i]
    #         for j in range(len(elli_x)):
    #             cv2.circle(new_mask, (elli_x[j], elli_y[j]), radius, color, thickness)
        
    blend_image = Image.blend(old_img,Image.fromarray(new_mask),0.7)
    # global blend_image_path
    blend_image_path=timestamp+'_blend_mask.png'
    blend_image.save(blend_image_path)

    if len(total_index)>0:
        tree_cloud=image_util.display_inlier_outlier(pred_cloud, total_index)
    else:
        tree_cloud=pred_cloud

    cl, ind = tree_cloud.remove_radius_outlier(nb_points=10, radius=1.5)#nb_points：球体中最少点的数量 radius球的半径
    radius_cloud = tree_cloud.select_by_index(ind)
    # global cloud_path
    cloud_path=model_type+'_'+timestamp+'_Depth_tree.ply'
    o3d.io.write_point_cloud(cloud_path, radius_cloud,write_ascii=True)
    # cloud_path=model_type+'_'+timestamp+'_Depth.ply'
    
    return selected_heights


def RemoveFiles():
    global pred_depth_image_path
    os.remove(pred_depth_image_path)
    global original_mask_path
    os.remove(original_mask_path)
    global threshold_mask_path
    os.remove(threshold_mask_path)
    # global final_mask_path
    # os.remove(final_mask_path)
    global blend_image_path
    os.remove(blend_image_path)


if __name__=='__main__':
    # app.run(host="0.0.0.0", port=81, debug=True)
    app.run(host="0.0.0.0", port=81,debug=False,threaded = True)

