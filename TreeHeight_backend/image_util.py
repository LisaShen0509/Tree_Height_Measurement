# -*- coding:UTF-8 -*-
import joblib
from PIL import Image
import open3d as o3d
import cv2
import numpy as np
import base64

#相机内参
# cam = joblib.load('mtx.pkl')

def Rel2Abs(x_rel,y_abs,n):
    '''
    将相对深度图调整到绝对深度图的值域
    x_rel:相对深度
    y_abs:绝对深度
    n：最高次数，阶数
    '''
    #collapsed into one dimension
    y = y_abs.copy().flatten() # Absolute Depth
    x = x_rel.copy().flatten()  # Relative Depth
#     A = np.vstack([x, np.ones(len(x))]).T
#     s, t = np.linalg.lstsq(A, y, rcond=None)[0]
#     return x_rel*s+t
    p = np.poly1d(np.polyfit(x,y,n)) #拟合并构造出一个n阶多项式
    depth_aligned=0.0
    for c in p.coeffs:
        depth_aligned*=x_rel
        depth_aligned += c
    return depth_aligned

def letterbox_image(image, size):
  '''
  添加灰条，修改图片尺寸
  '''
  image = image.convert("RGB")
  iw, ih = image.size#图片原尺寸
  w, h = size#需要的尺寸
  scale = min(w/iw, h/ih)#找到较小的倍数
  nw = int(iw*scale)
  nh = int(ih*scale)

  image = image.resize((nw,nh), Image.BICUBIC)
  new_image = Image.new('RGB', size, (128,128,128))#灰色底部
  new_image.paste(image, ((w-nw)//2, (h-nh)//2))
  return new_image,nw,nh

def letterbox_depth(depth, size):
  '''
  处理单通道的灰度图，同样修改图片尺寸
  '''
  iw, ih = depth.size#图片原尺寸
  w, h = size#需要的尺寸
  scale = min(w/iw, h/ih)#找到较小的倍数
  nw = int(iw*scale)
  nh = int(ih*scale)

  depth = depth.resize((nw,nh), Image.BICUBIC)
  new_depth = Image.new('L', size, 0)
  new_depth.paste(depth, ((w-nw)//2, (h-nh)//2))
  return new_depth,nw,nh

def CreatePointCloud(img,depth,fx,fy,cx,cy,scale,depthScale,fileName):
    '''
    img:RGB图
    depth：深度图 类型只能是uint8 uint16 float 若img和depth大小不一样会报错
    scale:img相对于相机获取的原始图像，被放缩的比例
    depthScale:深度图的数值和米的比值
    depthTrunc:大于这个值的深度看做0
    fileName:生成的点云的文件名
    '''
    height, width, _ = img.shape
    # fx = cam[0,0]*scale
    # fy = cam[1,1]*scale
    # cx = cam[0,2]*scale
    # cy = cam[1,2]*scale

    #焦距（fx，fy），光学中心（cx，cy）
    # 输入open3d能识别的相机内参，如果用自己的相机，则需要先做内参的转换
    # 存储相机内参和图像高和宽
    cam_o3 = o3d.camera.PinholeCameraIntrinsic(width, height, fx,fy,cx,cy)
    # 生成rgbd图，参数：彩色图，深度图，深度值与尺度的比率（默认为1000，深度值首先被缩放，然后被截断），
    # 深度值大于depth_trunc的值被截断为0，是否将RGB图像转换为强度图像

    rgbd_image = o3d.geometry.RGBDImage.create_from_color_and_depth(
            o3d.geometry.Image(img), o3d.geometry.Image(depth), 
        depth_scale=depthScale, depth_trunc=round(depth.max()/scale), convert_rgb_to_intensity=False)

    #生成三维点云需要rgbd图和相机内参
    pcd = o3d.geometry.PointCloud.create_from_rgbd_image(rgbd_image, cam_o3)
    pcd.transform([[1, 0, 0, 0], [0, -1, 0, 0], [0, 0, -1, 0], [0, 0, 0, 1]])#记得翻转它，否则则是倒着的点云
    # 把生成的点云显示出来
    # o3d.visualization.draw_geometries([pcd])
    o3d.io.write_point_cloud(fileName, pcd,write_ascii=True)
    return pcd


def find_max_region(mask_sel):
    contours,hierarchy = cv2.findContours(mask_sel,cv2.RETR_TREE, cv2.CHAIN_APPROX_NONE)
    area = []
    for j in range(len(contours)):
        area.append(cv2.contourArea(contours[j]))
    max_idx = np.argmax(area)
    # max_area = cv2.contourArea(contours[max_idx])
    for k in range(len(contours)):
        if k != max_idx:
            cv2.fillPoly(mask_sel, [contours[k]], 0)
    return mask_sel

def display_inlier_outlier(cloud, ind):
    inlier_cloud = cloud.select_by_index(ind)
    outlier_cloud = cloud.select_by_index(ind, invert=True) # 设置为True表示保存ind之外的点
#     outlier_cloud.paint_uniform_color([0, 1, 0])
#     inlier_cloud.paint_uniform_color([1, 0, 0])
    # o3d.visualization.draw_geometries([inlier_cloud],width=600,height=600)
    return inlier_cloud

def radius_outlier_removal(tree_cloud,minpoints,ball_radius):
    print("Radius oulier removal")
    cl, ind = tree_cloud.remove_radius_outlier(nb_points=minpoints, radius=ball_radius)#nb_points：球体中最少点的数量 radius球的半径
    radius_cloud = tree_cloud.select_by_index(ind)
    # o3d.visualization.draw_geometries([radius_cloud], window_name="半径滤波",
    #                                   width=700, height=700,
    #                                   left=50, top=50,
    #                                   mesh_show_back_face=False)
    return radius_cloud

def get_avg_distance(cloud,k):
    '''
    cloud：点云
    k：用来计算平均距离的k个最近邻居
    '''
    point = np.asarray(cloud.points)  # 获取点坐标
    kdtree = o3d.geometry.KDTreeFlann(cloud)  # 建立KD树索引
    point_size = point.shape[0]  # 获取点的个数
    dd = np.zeros(point_size)
    for i in range(point_size):
        [_, idx, dis] = kdtree.search_knn_vector_3d(point[i], k+1)
        dd[i] = np.mean(np.sqrt(dis[1:]))  # 获取到k个最近邻点的平均距离 第1个为到自己的距离
    return dd

def delete_given_points(cloud, ind):
    inlier_cloud = cloud.select_by_index(ind, invert=True)
    outlier_cloud = cloud.select_by_index(ind) # 设置为True表示保存ind之外的点
    outlier_cloud.paint_uniform_color([0, 1, 0])
#     inlier_cloud.paint_uniform_color([1, 0, 0])
    o3d.visualization.draw_geometries([inlier_cloud, outlier_cloud],width=600,height=600)
    return inlier_cloud

def stat_outlier_removal(radius_cloud,neighbors,std_threshold):
    print("Statistical oulier removal")
    # nb_neighbors:用于指定计算平均距离的邻域点的数量。
    # std_ratio:基于点云的平均距离的标准差来设置阈值。阈值越小，滤波效果越明显。
    cl, ind = radius_cloud.remove_statistical_outlier(nb_neighbors=neighbors,
                                             std_ratio=std_threshold)
    sor_cloud = radius_cloud.select_by_index(ind)
    o3d.visualization.draw_geometries([sor_cloud], window_name="统计滤波",
                                      width=700, height=700,
                                      left=50, top=50,
                                      mesh_show_back_face=False)
    return sor_cloud

def aabb(cloud):
    aabb = cloud.get_axis_aligned_bounding_box()
    aabb.color = (1, 0, 0)  # aabb包围盒为红色

    # [center_x, center_y, center_z] = aabb.get_center()
    # print("aabb包围盒的中心坐标为：\n", [center_x, center_y, center_z])

    # vertex_set = np.asarray(aabb.get_box_points())
    # print("obb包围盒的顶点为：\n", vertex_set)

    aabb_box_length = np.asarray(aabb.get_extent()) #x y z
    # print("aabb包围盒的边长为：\n", aabb_box_length)

    # half_extent = np.asarray(aabb.get_half_extent())
    # print("aabb包围盒边长的一半为：\n", half_extent)

    # max_bound = np.asarray(aabb.get_max_bound())
    # print("aabb包围盒边长的最大值为：\n", max_bound)

    # max_extent = np.asarray(aabb.get_max_extent())
    # print("aabb包围盒边长的最大范围，即X, Y和Z轴的最大值：\n", max_extent)

    # min_bound = np.asarray(aabb.get_min_bound())
    # print("aabb包围盒边长的最小值为：\n", min_bound)

    # o3d.visualization.draw_geometries([cloud, aabb], window_name="AABB包围盒",
    #                                   width=1024, height=768,
    #                                   left=50, top=50,
    #                                   mesh_show_back_face=False)
    
    return aabb_box_length

def image_to_base64(path):
    with open(path, 'rb') as f:
        code = base64.b64encode(f.read()).decode()
        return code
    
def cart2pol(x, y):  #笛卡尔坐标系->极坐标系
    theta = np.arctan2(y, x)#求夹角
    rho = np.hypot(x, y)#求斜边长度，即半径
    return theta, rho
def pol2cart(theta, rho):  #极坐标系->笛卡尔坐标系
    x = rho * np.cos(theta)
    y = rho * np.sin(theta)
    return x, y
def rotate_contour(cnt, rotatepoint, angle):  #轮廓旋转函数
    cx = rotatepoint[0]
    cy = rotatepoint[1]
    cnt_norm = cnt - [cx, cy]  #将轮廓移动到旋转点
    coordinates = cnt_norm[:, 0, :]#找到x=0的点
    xs, ys = coordinates[:, 0], coordinates[:, 1]#起点
    thetas, rhos = cart2pol(xs, ys)#起点转换成极坐标形式
    thetas = np.rad2deg(thetas)#弧度转角度
    thetas = (thetas + angle) % 360 #最终旋转角
    thetas = np.deg2rad(thetas)#转回幅度
    xs, ys = pol2cart(thetas, rhos)#起点转换回笛卡尔坐标系
    cnt_norm[:, 0, 0] = xs
    cnt_norm[:, 0, 1] = ys
    cnt_rotated = cnt_norm + [cx, cy] #轮廓平移回原来的位置
    cnt_rotated = cnt_rotated.astype(np.int32)
    return cnt_rotated

def find_max_region(mask_sel):
    contours,hierarchy = cv2.findContours(mask_sel,cv2.RETR_TREE, cv2.CHAIN_APPROX_NONE)
    area = []
    for j in range(len(contours)):
        area.append(cv2.contourArea(contours[j]))
    if len(area)>0:
        max_idx = np.argmax(area)
        max_area = cv2.contourArea(contours[max_idx])
        for k in range(len(contours)):
            if k != max_idx:
                cv2.fillPoly(mask_sel, [contours[k]], 0)
    return mask_sel